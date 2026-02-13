package com.ftxeven.aircore.core.chat.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public void clearAll() { cooldowns.clear(); }

    public boolean isOnCooldown(Player player) {
        long until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        return until > System.currentTimeMillis();
    }

    public double getRemaining(Player player) {
        long until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long diff = until - System.currentTimeMillis();
        return Math.max(0, diff / 1000.0);
    }

    public void apply(Player player, double seconds) {
        if (seconds <= 0) return;
        long duration = (long) (seconds * 1000L);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + duration);
    }

    public void clear(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}
