package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.repository.BlockRepository;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class BlockCache {

    private final BlockRepository repository;
    private final WriteBehind writes;
    private final ResidentStore<Set<UUID>> resident;

    public BlockCache(BlockRepository repository, WriteBehind writes, Predicate<UUID> isOnline, Logger logger) {
        this.repository = repository;
        this.writes = writes;
        this.resident = new ResidentStore<>("blocks", this::load, isOnline, Duration.ofMinutes(5), logger);
    }

    private Set<UUID> load(UUID owner) {
        return new CopyOnWriteArraySet<>(repository.blockedBy(owner));
    }

    public CompletableFuture<Void> warm(UUID owner) {
        return resident.load(owner);
    }

    public void release(UUID owner) {
        resident.release(owner);
    }

    public void invalidate(UUID owner) {
        resident.invalidate(owner);
    }

    public void invalidateAll() {
        resident.clear();
    }

    public void evictExpired() {
        resident.evictExpired();
    }

    public Set<UUID> blockedBy(UUID owner) {
        Set<UUID> live = resident.get(owner);
        return live != null ? Set.copyOf(live) : Set.of();
    }

    public boolean isBlocked(UUID owner, UUID target) {
        Set<UUID> live = resident.get(owner);
        return live != null && live.contains(target);
    }

    public int countBlocked(UUID owner) {
        Set<UUID> live = resident.get(owner);
        return live == null ? 0 : live.size();
    }

    public boolean block(UUID owner, UUID target) {
        Set<UUID> live = resident.get(owner);
        if (live == null) {
            resident.load(owner);
            return false;
        }
        boolean added = live.add(target);
        if (added) {
            writes.append(() -> repository.block(owner, target));
        }
        return added;
    }

    public boolean unblock(UUID owner, UUID target) {
        Set<UUID> live = resident.get(owner);
        if (live == null) {
            resident.load(owner);
            return false;
        }
        boolean removed = live.remove(target);
        if (removed) {
            writes.append(() -> repository.unblock(owner, target));
        }
        return removed;
    }

    public int unblockAll(UUID owner) {
        Set<UUID> live = resident.get(owner);
        if (live == null) {
            resident.load(owner);
            return 0;
        }
        int count = live.size();
        if (count == 0) {
            return 0;
        }
        live.clear();
        writes.append(() -> repository.unblockAll(owner));
        return count;
    }
}