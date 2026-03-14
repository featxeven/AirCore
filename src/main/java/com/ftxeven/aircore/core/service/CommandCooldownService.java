package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.config.ConfigManager.CooldownEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
        cooldowns.put(uuid, plugin.database().cooldowns().load(uuid));
    }

    public boolean isOnCooldown(Player player, String fullCommand) {
        return getRemaining(player, fullCommand) > 0;
    }

    public long getRemaining(Player player, String fullCommand) {
        CooldownEntry entry = plugin.config().findCooldownEntry(fullCommand);
        if (entry == null) return 0;

        if (player.hasPermission("aircore.bypass.command." + entry.id())) return 0;

        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        long expiry = playerCooldowns.getOrDefault(entry.id(), 0L);
        long remaining = (expiry - System.currentTimeMillis()) / 1000;

        return Math.max(0, remaining);
    }

    public void apply(UUID uuid, String fullCommand) {
        CooldownEntry entry = plugin.config().findCooldownEntry(fullCommand);
        if (entry == null || entry.seconds() <= 0) return;

        long expiry = System.currentTimeMillis() + (entry.seconds() * 1000L);
        cooldowns.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(entry.id(), expiry);

        plugin.database().cooldowns().save(uuid, entry.id(), expiry);
    }

    public void clear(UUID uuid) {
        cooldowns.remove(uuid);
    }
}