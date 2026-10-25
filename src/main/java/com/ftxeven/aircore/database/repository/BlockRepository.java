package com.ftxeven.aircore.database.repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlockRepository {

    record Entry(UUID blocked, Instant blockedAt) {}

    Set<UUID> blockedBy(UUID owner);

    List<Entry> export(UUID owner);

    boolean isBlocked(UUID owner, UUID target);

    int countBlocked(UUID owner);

    default boolean block(UUID owner, UUID target) {
        return block(owner, target, Instant.now());
    }

    boolean block(UUID owner, UUID target, Instant blockedAt);

    boolean unblock(UUID owner, UUID target);

    int unblockAll(UUID owner);
}