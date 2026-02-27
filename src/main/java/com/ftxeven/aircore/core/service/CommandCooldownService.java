package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;
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

    public String getActiveCooldownKey(UUID uuid, String fullCommand) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null || playerCooldowns.isEmpty()) return null;

        long now = System.currentTimeMillis();
        String bestMatch = null;

        for (Map.Entry<String, Long> entry : playerCooldowns.entrySet()) {
            String activeKey = entry.getKey();
            if (entry.getValue() <= now) continue;

            if (fullCommand.equalsIgnoreCase(activeKey) || fullCommand.toLowerCase().startsWith(activeKey.toLowerCase() + " ")) {
                if (bestMatch == null || activeKey.length() > bestMatch.length()) {
                    bestMatch = activeKey;
                }
            }
        }
        return bestMatch;
    }

    public boolean isOnCooldown(UUID uuid, String fullCommand) {
        return getActiveCooldownKey(uuid, fullCommand) != null;
    }

    public long getRemaining(UUID uuid, String fullCommand) {
        String key = getActiveCooldownKey(uuid, fullCommand);
        if (key == null) return 0;

        long expiry = cooldowns.get(uuid).getOrDefault(key, 0L);
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void apply(UUID uuid, String configKey, int seconds) {
        if (seconds <= 0) return;

        String cleanKey = configKey.startsWith("*") ? configKey.substring(1) : configKey;

        long expiry = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(cleanKey, expiry);

        plugin.database().cooldowns().save(uuid, cleanKey, expiry);
    }

    public void clear(UUID uuid) { cooldowns.remove(uuid); }
}