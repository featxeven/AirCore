package com.ftxeven.aircore.core.teleport.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final Map<String, Long> lastRequestTimes = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID sender, UUID target, int cooldownSeconds) {
        String key = sender + ":" + target;
        long now = System.currentTimeMillis();
        long last = lastRequestTimes.getOrDefault(key, 0L);
        return now - last < cooldownSeconds * 1000L;
    }

    public long getRemaining(UUID sender, UUID target, int cooldownSeconds) {
        String key = sender + ":" + target;
        long now = System.currentTimeMillis();
        long last = lastRequestTimes.getOrDefault(key, 0L);
        return ((last + cooldownSeconds * 1000L) - now) / 1000L + 1;
    }

    public void mark(UUID sender, UUID target) {
        lastRequestTimes.put(sender + ":" + target, System.currentTimeMillis());
    }

    public void clear(UUID uuid) {
        lastRequestTimes.keySet().removeIf(key ->
                key.startsWith(uuid.toString() + ":") || key.endsWith(":" + uuid));
    }
}
