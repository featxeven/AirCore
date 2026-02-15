package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final AirCore plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(AirCore plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return false;

        Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) return false;

        Long expiry = userCooldowns.get(item.key());
        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            userCooldowns.remove(item.key());
            return false;
        }
        return true;
    }

    public void applyCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return;

        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(item.key(), System.currentTimeMillis() + (long)(item.cooldown() * 1000));
    }

    public void sendCooldownMessage(Player player, GuiDefinition.GuiItem item) {
        String rawMessage = item.cooldownMessage();
        if (rawMessage == null || rawMessage.isBlank()) return;

        Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) return;

        long expiry = userCooldowns.getOrDefault(item.key(), 0L);
        long remainingMillis = expiry - System.currentTimeMillis();

        long remainingSeconds = Math.max(1, (long) Math.ceil(remainingMillis / 1000.0));

        String timeString = TimeUtil.formatSeconds(plugin, remainingSeconds);

        Map<String, String> placeholders = Map.of("cooldown", timeString);

        Component message = MessageUtil.mini(player, rawMessage, placeholders);

        if (message != null) {
            player.sendMessage(message);
        }
    }

    public void clear() {
        cooldowns.clear();
    }
}