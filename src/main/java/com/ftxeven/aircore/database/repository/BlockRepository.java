package com.ftxeven.aircore.database.repository;

import java.util.Set;
import java.util.UUID;

public interface BlockRepository {

    Set<UUID> blockedBy(UUID owner);

    boolean isBlocked(UUID owner, UUID target);

    int countBlocked(UUID owner);

    boolean block(UUID owner, UUID target); // false if already blocked

    boolean unblock(UUID owner, UUID target); // false if it wasn't blocked

    int unblockAll(UUID owner);
}