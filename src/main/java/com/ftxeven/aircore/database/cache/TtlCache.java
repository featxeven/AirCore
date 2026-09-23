package com.ftxeven.aircore.database.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TtlCache<K, V> {

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int maxSize;

    public TtlCache(Duration ttl) {
        this(ttl, -1);
    }

    public TtlCache(Duration ttl, int maxSize) {
        this.ttlNanos = ttl.toNanos();
        this.maxSize = maxSize;
    }

    public Optional<V> get(K key) {
        Entry<V> cached = entries.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.isExpired()) {
            entries.remove(key, cached);
            return Optional.empty();
        }
        return Optional.of(cached.value());
    }

    public V resolve(K key, Supplier<V> loader) {
        return get(key).orElseGet(() -> {
            V value = loader.get();
            put(key, value);
            return value;
        });
    }

    public void put(K key, V value) {
        if (maxSize > 0 && entries.size() >= maxSize) {
            entries.clear();
        }
        entries.put(key, new Entry<>(value, System.nanoTime() + ttlNanos));
    }

    public void remove(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    public List<V> liveValues() {
        List<V> live = new ArrayList<>(entries.size());
        for (Map.Entry<K, Entry<V>> entry : entries.entrySet()) {
            if (entry.getValue().isExpired()) {
                entries.remove(entry.getKey(), entry.getValue());
            } else {
                live.add(entry.getValue().value());
            }
        }
        return live;
    }

    private record Entry<V>(V value, long expiresAtNanos) {
        boolean isExpired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }
}