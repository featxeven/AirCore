package com.ftxeven.aircore.module.teleport.request;

import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.TeleportConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Cooldowns;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TeleportRequestHandler {

    public sealed interface SendResult {
        record Sent(int expireAfterSeconds) implements SendResult {}
        record Self() implements SendResult {}
        record Blocked() implements SendResult {}
        record TargetToggledOff() implements SendResult {}
        record OnCooldown(double remainingSeconds) implements SendResult {}
        record TargetAtLimit(int count, int limit) implements SendResult {}
    }

    public record PendingRequest(UUID sender, UUID target, TeleportType type, Instant expiresAt) {

        public UUID travellerUuid() {
            return type == TeleportType.TPAHERE ? target : sender;
        }

        public UUID anchorUuid() {
            return type == TeleportType.TPAHERE ? sender : target;
        }
    }

    private record Key(UUID sender, UUID target) {}

    private record Entry(PendingRequest request, @Nullable ScheduledTask expiryTask) {
        void cancelTimer() {
            if (expiryTask != null) {
                expiryTask.cancel();
            }
        }
    }

    private final Supplier<TeleportConfig> config;

    // target -> (sender -> pending request from that sender)
    private final Map<UUID, Map<UUID, Entry>> incoming = new ConcurrentHashMap<>();

    private final Map<UUID, LinkedHashSet<UUID>> incomingOrder = new ConcurrentHashMap<>(); // target -> senders
    private final Map<UUID, LinkedHashSet<UUID>> outgoingOrder = new ConcurrentHashMap<>(); // sender -> targets

    private final Cooldowns<Key> sendCooldowns = new Cooldowns<>();
    private final Cooldowns<UUID> sendAllCooldowns = new Cooldowns<>();

    private volatile Consumer<PendingRequest> onExpire = request -> { };

    public TeleportRequestHandler(Supplier<TeleportConfig> config) {
        this.config = config;
    }

    public void onExpire(Consumer<PendingRequest> listener) {
        this.onExpire = listener != null ? listener : request -> { };
    }

    // Sending

    public Optional<SendResult> preview(Player sender, UUID targetUuid, boolean targetAcceptsRequests, int targetMaxPending) {
        UUID senderUuid = sender.getUniqueId();
        if (senderUuid.equals(targetUuid)) {
            return Optional.of(new SendResult.Self());
        }
        if (!targetAcceptsRequests && !sender.hasPermission(Permissions.Bypass.TELEPORT_TOGGLE)) {
            return Optional.of(new SendResult.TargetToggledOff());
        }

        TeleportConfig.Requests requests = config.get().requests();
        if (!sender.hasPermission(Permissions.Bypass.TELEPORT_COOLDOWN)) {
            double remaining = sendCooldowns.remainingSeconds(new Key(senderUuid, targetUuid), requests.cooldown());
            if (remaining > 0) {
                return Optional.of(new SendResult.OnCooldown(remaining));
            }
        }

        if (targetMaxPending >= 0) {
            int pending = incoming.getOrDefault(targetUuid, Map.of()).size();
            if (pending >= targetMaxPending) {
                return Optional.of(new SendResult.TargetAtLimit(pending, targetMaxPending));
            }
        }

        return Optional.empty();
    }

    public SendResult send(Player sender, UUID targetUuid, TeleportType type, boolean targetAcceptsRequests, int targetMaxPending) {
        Optional<SendResult> blocked = preview(sender, targetUuid, targetAcceptsRequests, targetMaxPending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        UUID senderUuid = sender.getUniqueId();
        TeleportConfig.Requests requests = config.get().requests();
        sendCooldowns.hit(new Key(senderUuid, targetUuid));
        store(senderUuid, targetUuid, type, requests.expireAfter());
        return new SendResult.Sent(requests.expireAfter());
    }

    private void store(UUID senderUuid, UUID targetUuid, TeleportType type, int expireAfterSeconds) {
        remove(targetUuid, senderUuid); // a fresh request from the same sender replaces theirs

        Instant expiresAt = expireAfterSeconds > 0 ? Instant.now().plusSeconds(expireAfterSeconds) : Instant.MAX;
        PendingRequest request = new PendingRequest(senderUuid, targetUuid, type, expiresAt);

        ScheduledTask expiryTask = expireAfterSeconds > 0
                ? Scheduler.runGlobalLater(() -> expire(targetUuid, senderUuid), expireAfterSeconds * 20L)
                : null;

        incoming.computeIfAbsent(targetUuid, ignored -> new ConcurrentHashMap<>()).put(senderUuid, new Entry(request, expiryTask));
        markMostRecent(incomingOrder, targetUuid, senderUuid);
        markMostRecent(outgoingOrder, senderUuid, targetUuid);
    }

    // Sending (all)

    public OptionalDouble previewAll(Player sender) {
        if (sender.hasPermission(Permissions.Bypass.TELEPORT_COOLDOWN)) {
            return OptionalDouble.empty();
        }
        double remaining = sendAllCooldowns.remainingSeconds(sender.getUniqueId(), config.get().requests().cooldown());
        return remaining > 0 ? OptionalDouble.of(remaining) : OptionalDouble.empty();
    }

    public void markAllSent(Player sender) {
        sendAllCooldowns.hit(sender.getUniqueId());
    }

    // Resolving

    public Optional<PendingRequest> get(UUID targetUuid, UUID senderUuid) {
        Map<UUID, Entry> forTarget = incoming.get(targetUuid);
        return forTarget == null ? Optional.empty() : Optional.ofNullable(forTarget.get(senderUuid)).map(Entry::request);
    }

    // the request targetUuid received most recently and is still pending. Falls through to the
    // next-most-recent one automatically once the current top request is accepted/denied/expired
    public Optional<PendingRequest> mostRecentIncoming(UUID targetUuid) {
        return mostRecent(incomingOrder, targetUuid).flatMap(senderUuid -> get(targetUuid, senderUuid));
    }

    // same idea, for the requests senderUuid has sent out
    public Optional<PendingRequest> mostRecentOutgoing(UUID senderUuid) {
        return mostRecent(outgoingOrder, senderUuid).flatMap(targetUuid -> get(targetUuid, senderUuid));
    }

    public List<PendingRequest> incomingFor(UUID targetUuid) {
        Map<UUID, Entry> forTarget = incoming.get(targetUuid);
        if (forTarget == null || forTarget.isEmpty()) {
            return List.of();
        }
        return forTarget.values().stream().map(Entry::request).toList();
    }

    public Optional<PendingRequest> accept(UUID targetUuid, UUID senderUuid) {
        return remove(targetUuid, senderUuid);
    }

    public Optional<PendingRequest> deny(UUID targetUuid, UUID senderUuid) {
        return remove(targetUuid, senderUuid);
    }

    public Optional<PendingRequest> cancel(UUID senderUuid, UUID targetUuid) {
        return remove(targetUuid, senderUuid);
    }

    public List<PendingRequest> denyAll(UUID targetUuid) {
        return removeAll(targetUuid);
    }

    public List<PendingRequest> cancelAll(UUID senderUuid) {
        List<UUID> targets = snapshot(outgoingOrder, senderUuid);
        List<PendingRequest> cancelled = new ArrayList<>(targets.size());
        for (UUID targetUuid : targets) {
            remove(targetUuid, senderUuid).ifPresent(cancelled::add);
        }
        return cancelled;
    }

    private List<PendingRequest> removeAll(UUID targetUuid) {
        Map<UUID, Entry> forTarget = incoming.remove(targetUuid);
        if (forTarget == null || forTarget.isEmpty()) {
            return List.of();
        }
        List<PendingRequest> removed = new ArrayList<>(forTarget.size());
        for (Map.Entry<UUID, Entry> entry : forTarget.entrySet()) {
            entry.getValue().cancelTimer();
            UUID senderUuid = entry.getKey();
            forget(outgoingOrder, senderUuid, targetUuid);
            removed.add(entry.getValue().request());
        }
        incomingOrder.remove(targetUuid);
        return removed;
    }

    private Optional<PendingRequest> remove(UUID targetUuid, UUID senderUuid) {
        Entry[] removed = new Entry[1];
        incoming.computeIfPresent(targetUuid, (uuid, forTarget) -> {
            removed[0] = forTarget.remove(senderUuid);
            return forTarget.isEmpty() ? null : forTarget;
        });
        if (removed[0] == null) {
            return Optional.empty();
        }
        removed[0].cancelTimer();
        forget(incomingOrder, targetUuid, senderUuid);
        forget(outgoingOrder, senderUuid, targetUuid);
        return Optional.of(removed[0].request());
    }

    private void expire(UUID targetUuid, UUID senderUuid) {
        remove(targetUuid, senderUuid).ifPresent(onExpire::accept);
    }

    // Cleanup

    public void handleQuit(UUID uuid) {
        if (config.get().requests().retainOnLogout()) {
            return;
        }
        cancelAll(uuid); // requests they sent
        removeAll(uuid); // requests they received
    }

    // Recency-order bookkeeping

    private void markMostRecent(Map<UUID, LinkedHashSet<UUID>> order, UUID key, UUID value) {
        LinkedHashSet<UUID> set = order.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        synchronized (set) {
            set.remove(value);
            set.add(value);
        }
    }

    private void forget(Map<UUID, LinkedHashSet<UUID>> order, UUID key, UUID value) {
        LinkedHashSet<UUID> set = order.get(key);
        if (set == null) {
            return;
        }
        synchronized (set) {
            set.remove(value);
            if (set.isEmpty()) {
                order.remove(key, set);
            }
        }
    }

    private Optional<UUID> mostRecent(Map<UUID, LinkedHashSet<UUID>> order, UUID key) {
        LinkedHashSet<UUID> set = order.get(key);
        if (set == null) {
            return Optional.empty();
        }
        synchronized (set) {
            UUID last = null;
            for (UUID value : set) {
                last = value;
            }
            return Optional.ofNullable(last);
        }
    }

    private List<UUID> snapshot(Map<UUID, LinkedHashSet<UUID>> order, UUID key) {
        LinkedHashSet<UUID> set = order.get(key);
        if (set == null) {
            return List.of();
        }
        synchronized (set) {
            return List.copyOf(set);
        }
    }
}