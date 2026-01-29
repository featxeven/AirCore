package com.ftxeven.aircore.service;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class BlockService {
    private final AirCore plugin;
    private final Map<UUID, Set<UUID>> blockedPlayers = new ConcurrentHashMap<>();

    public BlockService(AirCore plugin) {
        this.plugin = plugin;
    }

    public enum Status {
        SUCCESS,
        ALREADY_BLOCKED,
        LIMIT_REACHED
    }

    public int getBlockLimit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return plugin.config().blocksMaxBlocks();
        }

        if (player.hasPermission("aircore.bypass.block.limit.*")) {
            return Integer.MAX_VALUE;
        }

        int limit = plugin.config().blocksMaxBlocks();
        for (int i = 1000; i >= limit; i--) {
            if (player.hasPermission("aircore.bypass.block.limit." + i)) {
                return i;
            }
        }
        return limit;
    }

    public Status block(UUID player, UUID target) {
        Set<UUID> set = blockedPlayers.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());

        if (set.contains(target)) {
            return Status.ALREADY_BLOCKED;
        }

        int limit = getBlockLimit(player);
        if (set.size() >= limit) {
            return Status.LIMIT_REACHED;
        }

        set.add(target);
        return Status.SUCCESS;
    }

    public void unblock(UUID player, UUID target) {
        Set<UUID> set = blockedPlayers.get(player);
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) blockedPlayers.remove(player);
        }
    }

    public boolean isBlocked(UUID player, UUID target) {
        Set<UUID> set = blockedPlayers.get(player);
        return set != null && set.contains(target);
    }

    public Set<UUID> getBlocked(UUID player) {
        return blockedPlayers.getOrDefault(player, Collections.emptySet());
    }
}
