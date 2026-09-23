package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.util.Scheduler;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player state read from the database once, off-thread, then served from memory only.
 */
public final class ResidentStore<V> {

    private static final long TOUCH_GRANULARITY_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final class Slot<T> {
        private final T value;
        private volatile long lastAccessNanos = System.nanoTime();

        private Slot(T value) {
            this.value = value;
        }
    }

    private final String label;
    private final Function<UUID, V> loader; // blocking, only ever invoked on an async thread
    private final Predicate<UUID> isOnline;
    private final long offlineGraceNanos;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Slot<V>> resident = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();

    public ResidentStore(String label, Function<UUID, V> loader, Predicate<UUID> isOnline,
                         Duration offlineGrace, Logger logger) {
        this.label = label;
        this.loader = loader;
        this.isOnline = isOnline;
        this.offlineGraceNanos = offlineGrace.toNanos();
        this.logger = logger;
    }

    /** The resident value, or null. A miss schedules a background load so the next call hits. */
    public @Nullable V get(UUID key) {
        Slot<V> slot = resident.get(key);
        if (slot == null) {
            load(key);
            return null;
        }
        long now = System.nanoTime();
        if (now - slot.lastAccessNanos > TOUCH_GRANULARITY_NANOS) {
            slot.lastAccessNanos = now;
        }
        return slot.value;
    }

    public boolean contains(UUID key) {
        return resident.containsKey(key);
    }

    /** completes immediately when resident; otherwise loads once. */
    public CompletableFuture<Void> load(UUID key) {
        if (resident.containsKey(key)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> inFlight = loading.putIfAbsent(key, created);
        if (inFlight != null) {
            return inFlight;
        }
        try {
            Scheduler.runAsync(() -> populate(key, created));
        } catch (RuntimeException e) {
            loading.remove(key, created);
            created.completeExceptionally(e);
        }
        return created;
    }

    private void populate(UUID key, CompletableFuture<Void> future) {
        try {
            if (!resident.containsKey(key)) {
                resident.putIfAbsent(key, new Slot<>(loader.apply(key)));
            }
            future.complete(null);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not load " + label + " for " + key, t);
            future.completeExceptionally(t);
        } finally {
            loading.remove(key, future);
        }
    }

    /** The owner left: keep the entry, start its grace period. */
    public void release(UUID key) {
        Slot<V> slot = resident.get(key);
        if (slot != null) {
            slot.lastAccessNanos = System.nanoTime();
        }
    }

    /** Hard drop, for when the database was changed behind the cache's back (migrations). */
    public void invalidate(UUID key) {
        resident.remove(key);
    }

    public void clear() {
        resident.clear();
    }

    public int evictExpired() {
        long now = System.nanoTime();
        int evicted = 0;
        for (var entry : resident.entrySet()) {
            Slot<V> slot = entry.getValue();
            if (now - slot.lastAccessNanos < offlineGraceNanos) {
                continue;
            }
            if (isOnline.test(entry.getKey())) {
                slot.lastAccessNanos = now;
            } else if (resident.remove(entry.getKey(), slot)) {
                evicted++;
            }
        }
        return evicted;
    }
}