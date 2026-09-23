package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.util.Scheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownCache {

    private record Key(CooldownScope scope, UUID owner, String key, String arg) {}

    private final CooldownRepository repository;
    private final WriteBehind writes;
    private final ConcurrentHashMap<Key, Instant> cache = new ConcurrentHashMap<>();

    public CooldownCache(CooldownRepository repository, WriteBehind writes) {
        this.repository = repository;
        this.writes = writes;
    }

    public Optional<Cooldown> peek(CooldownScope scope, UUID owner, String key, String arg, Instant now) {
        Key cacheKey = new Key(scope, owner, key, arg);
        Instant cached = cache.get(cacheKey);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.isAfter(now)) {
            return Optional.of(toModel(scope, owner, key, arg, cached));
        }
        cache.remove(cacheKey, cached); // expired, drop the stale entry
        return Optional.empty();
    }

    public void warm(UUID owner, Instant now) {
        Scheduler.runAsync(() -> {
            for (Cooldown cooldown : repository.findAllActive(owner, now)) {
                cache.put(new Key(cooldown.scope(), owner, cooldown.key(), cooldown.arg()), cooldown.expiresAt());
            }
        });
    }

    public void start(CooldownScope scope, UUID owner, String key, String arg, Instant expiresAt) {
        Key cacheKey = new Key(scope, owner, key, arg);
        cache.put(cacheKey, expiresAt);
        writes.submit(cacheKey, () -> repository.set(owner, scope, key, arg, expiresAt));
    }

    public void clear(CooldownScope scope, UUID owner, String key, String arg) {
        Key cacheKey = new Key(scope, owner, key, arg);
        cache.remove(cacheKey);
        writes.submit(cacheKey, () -> repository.clear(owner, scope, key, arg));
    }

    public void invalidate(UUID owner) {
        cache.keySet().removeIf(k -> k.owner().equals(owner));
    }

    public void clearAllForKey(CooldownScope scope, String key) {
        cache.keySet().removeIf(k -> k.scope() == scope && k.key().equals(key));
        writes.append(() -> repository.clearAllForKey(scope, key));
    }

    public int purgeExpired(Instant before) {
        return repository.purgeExpired(before);
    }

    private Cooldown toModel(CooldownScope scope, UUID owner, String key, String arg, Instant expiresAt) {
        return new Cooldown(owner, scope, key, arg, expiresAt);
    }
}