package com.ftxeven.aircore.service.inventory;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.repository.PlayerInventoryRepository;
import com.ftxeven.aircore.model.PlayerInventory;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class InventoryService {

    private static final long EVICTION_DELAY_TICKS = 20L;
    private static final int LIVE_WRITE_ATTEMPTS = 2;

    private record SessionKey(UUID target, InventoryKind kind) {}

    // one slot a staff member edited while the player was offline: what the slot held when the session opened
    // (expected), and what it should hold now (item)
    private record Edit(int slot, @Nullable ItemStack expected, @Nullable ItemStack item) {}

    // immutable copy of a session's offline edits, so it can be applied on the player's thread while the session itself
    // keeps living on the global one
    private record PendingEdits(List<Edit> edits, int heldSlot) {

        static PendingEdits snapshot(Session.Backing.Offline offline) {
            List<Edit> edits = new ArrayList<>(offline.dirtySlots().size());
            for (int slot : offline.dirtySlots()) {
                if (slot < 0 || slot >= offline.items().length || slot >= offline.baseline().length) {
                    continue;
                }
                edits.add(new Edit(slot, ItemStackSync.clone(offline.baseline()[slot]), ItemStackSync.clone(offline.items()[slot])));
            }
            return new PendingEdits(List.copyOf(edits), offline.heldSlot());
        }
    }

    private final Logger logger;
    private final PlayerInventoryRepository repository;
    private final Map<SessionKey, Session> sessions = new ConcurrentHashMap<>();
    private final Map<SessionKey, LiveInventoryWatcher> liveWatchers = new ConcurrentHashMap<>();

    private final WriteBehind writes;

    public InventoryService(Logger logger, PlayerInventoryRepository repository, WriteBehind writes) {
        this.logger = logger;
        this.repository = repository;
        this.writes = writes;
    }

    // GUI-facing API

    public CompletableFuture<InventoryHandle.Opened> open(UUID viewer, UUID target, InventoryKind kind, boolean canModify) {
        return open(viewer, target, kind, canModify, null);
    }

    public CompletableFuture<InventoryHandle.Opened> open(UUID viewer, UUID target, InventoryKind kind, boolean canModify,
                                                          @Nullable Consumer<InventoryHandle.SlotChange> onRemoteChange) {
        CompletableFuture<InventoryHandle.Opened> future = new CompletableFuture<>();
        Scheduler.runGlobal(() -> {
            SessionKey key = new SessionKey(target, kind);
            Session session = sessions.computeIfAbsent(key, k -> new Session(k, repository, () -> refreshLiveWatcher(k)));
            session.acquire(viewer);
            if (onRemoteChange != null) {
                session.addListener(viewer, onRemoteChange);
            }
            session.whenReady(() -> {
                InventoryHandle handle = new InventoryHandle(this, viewer, target, kind, canModify);
                snapshotOf(session, kind).whenComplete((contents, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(new InventoryHandle.Opened(handle, contents));
                    }
                });
            });
        });
        return future;
    }

    void release(UUID target, InventoryKind kind, UUID viewer) {
        Scheduler.runGlobal(() -> releaseInternal(new SessionKey(target, kind), viewer));
    }

    public void handleViewerDisconnect(UUID viewer) {
        Scheduler.runGlobal(() -> {
            for (SessionKey key : sessions.keySet()) {
                releaseInternal(key, viewer);
            }
        });
    }

    private void releaseInternal(SessionKey key, UUID viewer) {
        Session session = sessions.get(key);
        if (session == null) {
            return;
        }
        session.removeListener(viewer);
        if (session.release(viewer)) {
            Scheduler.runGlobalLater(() -> evictIfUnused(key), EVICTION_DELAY_TICKS);
        }
    }

    private void evictIfUnused(SessionKey key) {
        Session session = sessions.get(key);
        if (session != null && session.isUnused()) {
            sessions.remove(key);
            stopWatcher(key);
        }
    }

    CompletableFuture<Boolean> isLive(UUID target, InventoryKind kind) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Scheduler.runGlobal(() -> {
            Session session = sessions.get(new SessionKey(target, kind));
            future.complete(session != null && session.backing instanceof Session.Backing.Live);
        });
        return future;
    }

    CompletableFuture<Integer> size(UUID target, InventoryKind kind) {
        return dispatch(target, kind,
                player -> containerFor(player, kind).getSize(),
                offline -> offline.items().length);
    }

    CompletableFuture<ItemStack> get(UUID target, InventoryKind kind, int slot) {
        return dispatch(target, kind,
                player -> ItemStackSync.clone(containerFor(player, kind).getItem(slot)),
                offline -> ItemStackSync.clone((slot >= 0 && slot < offline.items().length) ? offline.items()[slot] : null));
    }

    CompletableFuture<ItemStack[]> snapshot(UUID target, InventoryKind kind) {
        return dispatch(target, kind,
                player -> captureLive(player, kind),
                offline -> cloneArray(offline.items()));
    }

    private CompletableFuture<ItemStack[]> snapshotOf(Session session, InventoryKind kind) {
        return dispatchOnBacking(session.backing,
                player -> captureLive(player, kind),
                offline -> cloneArray(offline.items()));
    }

    CompletableFuture<Boolean> set(UUID target, InventoryKind kind, int slot, @Nullable ItemStack item, UUID editor) {
        ItemStack toStore = sanitize(item);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Scheduler.runGlobal(() -> setOnGlobalThread(target, kind, slot, toStore, editor, future, LIVE_WRITE_ATTEMPTS));
        return future;
    }

    private void setOnGlobalThread(UUID target, InventoryKind kind, int slot, @Nullable ItemStack item,
                                   UUID editor, CompletableFuture<Boolean> future, int attemptsLeft) {
        SessionKey key = new SessionKey(target, kind);
        Session session = sessions.get(key);
        if (session == null || session.backing == null) {
            future.complete(false);
            return;
        }
        switch (session.backing) {
            case Session.Backing.Offline offline -> {
                if (slot < 0 || slot >= offline.items().length) {
                    future.complete(false);
                    return;
                }
                offline.items()[slot] = item;
                offline.dirtySlots().add(slot);
                session.notifyChange(slot, item, editor);
                enqueuePersist(target, kind, offline.items(), offline.heldSlot());
                future.complete(true);
            }
            case Session.Backing.Live live -> {
                Optional<ScheduledTask> scheduled = Scheduler.runEntity(live.player(), () -> {
                    Inventory container = containerFor(live.player(), kind);
                    if (slot < 0 || slot >= container.getSize()) {
                        future.complete(false);
                        return;
                    }
                    container.setItem(slot, item);
                    LiveInventoryWatcher watcher = liveWatchers.get(key);
                    if (watcher != null) {
                        watcher.acknowledge(slot, item);
                    }
                    Scheduler.runGlobal(() -> {
                        session.notifyChange(slot, item, editor);
                        future.complete(true);
                    });
                }, () -> retryOrFail(target, kind, slot, item, editor, future, attemptsLeft));
                if (scheduled.isEmpty()) {
                    retryOrFail(target, kind, slot, item, editor, future, attemptsLeft);
                }
            }
        }
    }

    private void retryOrFail(UUID target, InventoryKind kind, int slot, @Nullable ItemStack item,
                             UUID editor, CompletableFuture<Boolean> future, int attemptsLeft) {
        if (attemptsLeft <= 1) {
            future.complete(false);
            return;
        }
        Scheduler.runGlobalLater(
                () -> setOnGlobalThread(target, kind, slot, item, editor, future, attemptsLeft - 1), 1L);
    }

    // Live-inventory watching

    private void refreshLiveWatcher(SessionKey key) {
        stopWatcher(key);
        Session session = sessions.get(key);
        if (session != null && session.backing instanceof Session.Backing.Live live) {
            LiveInventoryWatcher watcher = new LiveInventoryWatcher(live.player(), () -> containerFor(live.player(), key.kind()),
                    (slot, item) -> session.notifyChange(slot, item, null));
            watcher.start();
            liveWatchers.put(key, watcher);
        }
    }

    private void stopWatcher(SessionKey key) {
        LiveInventoryWatcher watcher = liveWatchers.remove(key);
        if (watcher != null) {
            watcher.stop();
        }
    }

    // Durable persistence

    private void enqueuePersist(UUID target, InventoryKind kind, ItemStack[] snapshot, int heldSlot) {
        ItemStack[] copy = cloneArray(snapshot);
        writes.submit(new SessionKey(target, kind), () -> {
            if (kind == InventoryKind.MAIN) {
                repository.saveContents(target, copy, heldSlot);
            } else {
                repository.saveEnderChest(target, copy);
            }
        });
    }

    private <T> CompletableFuture<T> dispatch(UUID target, InventoryKind kind,
                                              Function<Player, T> onLive,
                                              Function<Session.Backing.Offline, T> onOffline) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Scheduler.runGlobal(() -> {
            Session session = sessions.get(new SessionKey(target, kind));
            if (session == null || session.backing == null) {
                future.completeExceptionally(new IllegalStateException("No open session for " + target + "/" + kind));
                return;
            }
            relay(dispatchOnBacking(session.backing, onLive, onOffline), future);
        });
        return future;
    }

    private <T> CompletableFuture<T> dispatchOnBacking(Session.Backing backing,
                                                       Function<Player, T> onLive,
                                                       Function<Session.Backing.Offline, T> onOffline) {
        CompletableFuture<T> future = new CompletableFuture<>();
        switch (backing) {
            case Session.Backing.Offline offline -> future.complete(onOffline.apply(offline));
            case Session.Backing.Live live -> {
                Optional<ScheduledTask> scheduled = Scheduler.runEntity(live.player(),
                        () -> future.complete(onLive.apply(live.player())),
                        () -> future.completeExceptionally(new IllegalStateException("Target went offline")));
                if (scheduled.isEmpty()) {
                    future.completeExceptionally(new IllegalStateException("Target went offline"));
                }
            }
        }
        return future;
    }

    private static <T> void relay(CompletableFuture<T> source, CompletableFuture<T> target) {
        source.whenComplete((value, error) -> {
            if (error != null) {
                target.completeExceptionally(error);
            } else {
                target.complete(value);
            }
        });
    }

    private static Inventory containerFor(Player player, InventoryKind kind) {
        return kind == InventoryKind.MAIN ? player.getInventory() : player.getEnderChest();
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = ItemStackSync.clone(source[i]);
        }
        return copy;
    }

    private static ItemStack sanitize(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemStack clone = item.clone();
        int max = clone.getMaxStackSize();
        if (clone.getAmount() > max) {
            clone.setAmount(max);
        } else if (clone.getAmount() < 1) {
            clone.setAmount(1);
        }
        return clone;
    }

    // Join/quit hooks

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] main = captureLive(player, InventoryKind.MAIN);
        ItemStack[] ender = captureLive(player, InventoryKind.ENDER);
        int heldSlot = player.getInventory().getHeldItemSlot();

        enqueuePersist(uuid, InventoryKind.MAIN, main, heldSlot);
        enqueuePersist(uuid, InventoryKind.ENDER, ender, heldSlot);

        Scheduler.runGlobal(() -> {
            adoptOffline(uuid, InventoryKind.MAIN, main, heldSlot);
            adoptOffline(uuid, InventoryKind.ENDER, ender, heldSlot);
        });
    }

    private void adoptOffline(UUID target, InventoryKind kind, ItemStack[] items, int heldSlot) {
        Session session = sessions.get(new SessionKey(target, kind));
        if (session != null && session.backing != null) {
            session.adoptOffline(items, heldSlot);
        }
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Scheduler.runGlobal(() -> {
            resumeSessionOnJoin(player, uuid, InventoryKind.MAIN);
            resumeSessionOnJoin(player, uuid, InventoryKind.ENDER);
        });
    }

    private void resumeSessionOnJoin(Player player, UUID uuid, InventoryKind kind) {
        Session session = sessions.get(new SessionKey(uuid, kind));
        if (session == null || !(session.backing instanceof Session.Backing.Offline offline)) {
            return;
        }

        PendingEdits pending = PendingEdits.snapshot(offline);
        // if the player is already gone the session just stays offline-backed, handleQuit() re-adopts it anyway
        Scheduler.runEntity(player, () -> {
            applyEdits(player, kind, pending);
            ItemStack[] actual = captureLive(player, kind); // the real state
            Scheduler.runGlobal(() -> session.resumeLive(player, actual));
        });
    }

    private void applyEdits(Player player, InventoryKind kind, PendingEdits pending) {
        if (pending.edits().isEmpty()) {
            return;
        }

        Inventory destination = containerFor(player, kind);
        for (Edit edit : pending.edits()) {
            if (edit.slot() >= destination.getSize()) {
                continue;
            }
            ItemStack liveNow = destination.getItem(edit.slot());
            if (!ItemStackSync.matches(liveNow, edit.expected())) {
                logger.warning("Skipping offline inventory edit on slot " + edit.slot() + " for " + player.getUniqueId()
                        + " (" + kind + "): live slot changed while player was offline (expected "
                        + describe(edit.expected()) + ", found " + describe(liveNow) + ")");
                continue;
            }
            destination.setItem(edit.slot(), edit.item());
        }

        if (kind == InventoryKind.MAIN) {
            applyHeldSlot(player, pending.heldSlot());
        }
    }

    private static String describe(@Nullable ItemStack item) {
        return ItemStackSync.isEmpty(item) ? "empty" : item.getType() + " x" + item.getAmount();
    }

    private void applyHeldSlot(Player player, int heldSlot) {
        if (heldSlot >= 0 && heldSlot < 9) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }
    }

    private ItemStack[] captureLive(Player player, InventoryKind kind) {
        int size = kind == InventoryKind.MAIN ? PlayerInventory.MAIN_SIZE : PlayerInventory.ENDERCHEST_SIZE;
        Inventory source = containerFor(player, kind);
        ItemStack[] captured = new ItemStack[size];
        int liveSize = Math.min(size, source.getSize());
        for (int i = 0; i < liveSize; i++) {
            captured[i] = ItemStackSync.clone(source.getItem(i));
        }
        return captured;
    }

    // Internal session bookkeeping

    private static final class Session {

        sealed interface Backing {
            record Live(Player player) implements Backing {}
            record Offline(ItemStack[] items, ItemStack[] baseline, int heldSlot, Set<Integer> dirtySlots) implements Backing {}
        }

        private final SessionKey key;
        private final PlayerInventoryRepository repository;
        private final Runnable onBackingChanged;
        private final List<Runnable> pendingOnLoad = new ArrayList<>();
        private final Map<UUID, Consumer<InventoryHandle.SlotChange>> listeners = new ConcurrentHashMap<>();
        private final Set<UUID> viewers = new HashSet<>();
        private volatile Backing backing;
        private boolean loading;

        private Session(SessionKey key, PlayerInventoryRepository repository, Runnable onBackingChanged) {
            this.key = key;
            this.repository = repository;
            this.onBackingChanged = onBackingChanged;
        }

        void acquire(UUID viewer) { viewers.add(viewer); }

        /** @return true once this was the last viewer holding the session open */
        boolean release(UUID viewer) {
            viewers.remove(viewer);
            return viewers.isEmpty();
        }

        boolean isUnused() { return viewers.isEmpty(); }

        void addListener(UUID viewer, Consumer<InventoryHandle.SlotChange> listener) {
            listeners.put(viewer, listener);
        }

        void removeListener(UUID viewer) {
            listeners.remove(viewer);
        }

        void whenReady(Runnable action) {
            if (backing != null) {
                action.run();
                return;
            }
            pendingOnLoad.add(action);
            if (!loading) {
                loading = true;
                beginLoad();
            }
        }

        private void beginLoad() {
            Player online = Bukkit.getPlayer(key.target());
            if (online != null) {
                complete(new Backing.Live(online));
                return;
            }
            int size = key.kind() == InventoryKind.MAIN ? PlayerInventory.MAIN_SIZE : PlayerInventory.ENDERCHEST_SIZE;
            Scheduler.runAsync(() -> {
                Optional<PlayerInventory> stored = repository.find(key.target());
                ItemStack[] items = extract(stored, key.kind(), size);
                int heldSlot = stored.map(PlayerInventory::heldSlot).orElse(0);
                Scheduler.runGlobal(() -> {
                    Player nowOnline = Bukkit.getPlayer(key.target());
                    complete(nowOnline != null
                            ? new Backing.Live(nowOnline)
                            : new Backing.Offline(items, cloneArray(items), heldSlot, new HashSet<>()));
                });
            });
        }

        private void complete(Backing resolved) {
            this.loading = false;
            List<Runnable> ready = List.copyOf(pendingOnLoad);
            pendingOnLoad.clear();
            setBacking(resolved);
            ready.forEach(Runnable::run);
        }

        private void setBacking(Backing backing) {
            this.backing = backing;
            onBackingChanged.run();
        }

        void adoptOffline(ItemStack[] items, int heldSlot) {
            setBacking(new Backing.Offline(items, cloneArray(items), heldSlot, new HashSet<>()));
            notifyFullRefresh(items);
        }

        void resumeLive(Player player, ItemStack[] actualContents) {
            setBacking(new Backing.Live(player));
            notifyFullRefresh(actualContents);
        }

        private static ItemStack[] extract(Optional<PlayerInventory> stored, InventoryKind kind, int size) {
            if (stored.isEmpty()) {
                return new ItemStack[size];
            }
            ItemStack[] source = kind == InventoryKind.MAIN ? stored.get().contents() : stored.get().enderChest();
            return java.util.Arrays.copyOf(source, size);
        }

        void notifyChange(int slot, @Nullable ItemStack item, @Nullable UUID changedBy) {
            InventoryHandle.SlotChange change = new InventoryHandle.SlotChange(slot, item, changedBy);
            listeners.forEach((viewer, listener) -> {
                if (!Objects.equals(viewer, changedBy)) {
                    listener.accept(change);
                }
            });
        }

        void notifyFullRefresh(ItemStack[] items) {
            for (int i = 0; i < items.length; i++) {
                notifyChange(i, items[i], null);
            }
        }
    }
}