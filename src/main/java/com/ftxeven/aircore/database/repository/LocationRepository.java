package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.NamedLocation;

import java.util.List;
import java.util.Optional;

public interface LocationRepository {

    enum Category { WARP, SPAWN }

    String SPAWN_DEFAULT = "default";
    String SPAWN_FIRST_JOIN = "first-join";
    String SPAWN_GROUP_PREFIX = "group:";

    static String spawnGroup(String groupName) {
        return SPAWN_GROUP_PREFIX + groupName;
    }

    static Optional<String> groupNameFromSpawnKey(String key) {
        return key.startsWith(SPAWN_GROUP_PREFIX) ? Optional.of(key.substring(SPAWN_GROUP_PREFIX.length())) : Optional.empty();
    }

    Optional<NamedLocation> find(Category category, String key);

    List<NamedLocation> findAll(Category category);

    void save(Category category, NamedLocation location);

    boolean delete(Category category, String key);
}