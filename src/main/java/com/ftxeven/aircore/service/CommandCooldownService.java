package com.ftxeven.aircore.service;

import com.ftxeven.aircore.AirCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandCooldownService {

    private final AirCore plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CommandCooldownService(AirCore plugin) {
        this.plugin = plugin;
    }

    public void load(UUID uuid) {
        var playerCooldowns = plugin.database().cooldowns().load(uuid);
        cooldowns.put(uuid, playerCooldowns);
    }

    public boolean isOnCooldown(UUID uuid, String command) {
        Map<String, Long> playerCooldowns = cooldowns.getOrDefault(uuid, new HashMap<>());
        long expiry = playerCooldowns.getOrDefault(command, 0L);
        return expiry > System.currentTimeMillis();
    }

    public long getRemaining(UUID uuid, String command) {
        Map<String, Long> playerCooldowns = cooldowns.getOrDefault(uuid, new HashMap<>());
        long expiry = playerCooldowns.getOrDefault(command, 0L);
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public void apply(UUID uuid, String command, int seconds) {
        if (seconds <= 0) return;
        long expiry = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>())
                .put(command, expiry);
        plugin.database().cooldowns().save(uuid, command, expiry);
    }

    public void clear(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public void clear(UUID uuid, String command) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(command);
            plugin.database().cooldowns().delete(uuid, command);
        }
    }
}