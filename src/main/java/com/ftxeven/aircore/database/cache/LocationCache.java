package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.database.repository.LocationRepository.Category;
import com.ftxeven.aircore.model.NamedLocation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class LocationCache {

    private record RowKey(Category category, String key) {}

    private final LocationRepository repository;
    private final WriteBehind writes;
    private final TtlCache<Category, Map<String, NamedLocation>> cache;

    public LocationCache(LocationRepository repository, WriteBehind writes, Duration ttl) {
        this.repository = repository;
        this.writes = writes;
        this.cache = new TtlCache<>(ttl);
    }

    public Optional<NamedLocation> find(Category category, String key) {
        return Optional.ofNullable(all(category).get(key));
    }

    public List<NamedLocation> findAll(Category category) {
        List<NamedLocation> result = new ArrayList<>(all(category).values());
        result.sort((a, b) -> a.key().compareToIgnoreCase(b.key()));
        return result;
    }

    public void save(Category category, NamedLocation location) {
        all(category).put(location.key(), location);
        writes.submit(new RowKey(category, location.key()), () -> repository.save(category, location));
    }

    public boolean delete(Category category, String key) {
        NamedLocation stored = all(category).remove(key);
        if (stored == null) {
            return false;
        }
        writes.submit(new RowKey(category, stored.key()), () -> repository.delete(category, stored.key()));
        return true;
    }

    private Map<String, NamedLocation> all(Category category) {
        return cache.resolve(category, () -> {
            Map<String, NamedLocation> loaded = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
            for (NamedLocation location : repository.findAll(category)) {
                loaded.put(location.key(), location);
            }
            return loaded;
        });
    }
}