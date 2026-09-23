package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.repository.KitRepository;
import com.ftxeven.aircore.model.Kit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class KitCache {

    private static final String ALL_KEY = "ALL";

    private final KitRepository repository;
    private final WriteBehind writes;
    private final TtlCache<String, Map<String, Kit>> cache;

    public KitCache(KitRepository repository, WriteBehind writes, Duration ttl) {
        this.repository = repository;
        this.writes = writes;
        this.cache = new TtlCache<>(ttl);
    }

    public Optional<Kit> find(String name) {
        return Optional.ofNullable(all().get(name));
    }

    public List<Kit> findAll() {
        return List.copyOf(all().values());
    }

    public void save(Kit kit) {
        all().put(kit.name(), kit);
        writes.submit(kit.name(), () -> repository.save(kit));
    }

    public boolean delete(String name) {
        Kit stored = all().remove(name);
        if (stored == null) {
            return false;
        }
        writes.submit(stored.name(), () -> repository.delete(stored.name()));
        return true;
    }

    private Map<String, Kit> all() {
        return cache.resolve(ALL_KEY, () -> {
            Map<String, Kit> loaded = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Kit kit : repository.findAll()) {
                loaded.put(kit.name(), kit);
            }
            return loaded;
        });
    }
}