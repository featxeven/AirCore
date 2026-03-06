package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCooldownHandler {
    private final AirCore plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public ItemCooldownHandler(AirCore plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return false;

        Map<String, Long> userMap = cooldowns.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        long expiry = userMap.getOrDefault(item.key(), 0L);

        if (System.currentTimeMillis() < expiry) return true;

        if (expiry != 0L) cooldowns.get(player.getUniqueId()).remove(item.key());
        return false;
    }

    public void applyCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return;

        long expiry = System.currentTimeMillis() + (long) (item.cooldown() * 1000);
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(item.key(), expiry);
    }

    public void sendCooldownMessage(Player player, GuiDefinition.GuiItem item) {
        String raw = item.cooldownMessage();
        if (raw == null || raw.isBlank()) return;

        long expiry = cooldowns.getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(item.key(), 0L);

        long remaining = Math.max(1, (long) Math.ceil((expiry - System.currentTimeMillis()) / 1000.0));
        String timeStr = TimeUtil.formatSeconds(plugin, remaining);

        player.sendMessage(MessageUtil.mini(player, raw, Map.of("cooldown", timeStr)));
    }

    public void clear() {
        cooldowns.clear();
    }
}