package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.database.repository.VariableRepository;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VariableBucket {

    @FunctionalInterface
    public interface Transform {
        @Nullable String apply(@Nullable String current);
    }

    private static final long TOUCH_GRANULARITY_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final String ownerId;
    private final ConcurrentHashMap<String, String> values;
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final Queue<VariableBucket> flushQueue;

    private volatile boolean discarded;
    private volatile long lastAccessNanos = System.nanoTime();
    private volatile long evictMark = Long.MIN_VALUE;

    VariableBucket(String ownerId, Map<String, String> initial, Queue<VariableBucket> flushQueue) {
        this.ownerId = ownerId;
        this.values = new ConcurrentHashMap<>(Math.max(4, initial.size()));
        this.values.putAll(initial);
        this.flushQueue = flushQueue;
    }

    public @Nullable String get(String key) {
        return values.get(key);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(values);
    }

    public @Nullable String update(String key, Transform transform) {
        String next = values.compute(key, (k, current) -> transform.apply(current));
        markDirty(key); // strictly after the value is visible
        return next;
    }

    // Persistence bookkeeping (store only)

    private void markDirty(String key) {
        dirty.add(key);
        if (queued.compareAndSet(false, true)) {
            flushQueue.add(this);
        }
    }

    void remark(String key) {
        markDirty(key);
    }

    void drain(VariableStore.Projection projection, List<VariableRepository.Change> out, List<VariableBucket> sources) {
        queued.set(false);
        if (discarded) {
            dirty.clear();
            return;
        }
        for (Iterator<String> it = dirty.iterator(); it.hasNext(); ) {
            String key = it.next();
            it.remove();
            String value = values.get(key);
            out.add(new VariableRepository.Change(ownerId, key, value, value == null ? null : projection.numeric(key, value)));
            sources.add(this);
        }
    }

    boolean hasPendingWrites() {
        return queued.get() || !dirty.isEmpty();
    }

    void retain(Set<String> known) {
        values.keySet().retainAll(known);
        dirty.retainAll(known);
    }

    void discard() {
        discarded = true;
    }

    // Residency (store only)

    void touch() {
        long now = System.nanoTime();
        if (now - lastAccessNanos > TOUCH_GRANULARITY_NANOS) {
            lastAccessNanos = now;
        }
    }

    void touchNow() {
        lastAccessNanos = System.nanoTime();
    }

    long idleNanos() {
        return System.nanoTime() - lastAccessNanos;
    }

    // two-phase eviction: a bucket is only dropped if it stayed untouched across two consecutive sweeps
    boolean confirmEviction() {
        long seen = lastAccessNanos;
        if (evictMark == seen) {
            return true;
        }
        evictMark = seen;
        return false;
    }

    void cancelEviction() {
        evictMark = Long.MIN_VALUE;
    }
}