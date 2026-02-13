package com.ftxeven.aircore.core.utility.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkService {
    private final Map<UUID, Long> afkMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCleared = new ConcurrentHashMap<>();

    public void setAfk(UUID uuid) {
        afkMap.put(uuid, System.currentTimeMillis());
    }

    public boolean isAfk(UUID uuid) {
        return afkMap.containsKey(uuid);
    }

    public boolean wasRecentlyCleared(UUID uuid) {
        Long time = lastCleared.get(uuid);
        return time != null && (System.currentTimeMillis() - time) < 500;
    }

    public long clearAfk(UUID uuid) {
        Long start = afkMap.remove(uuid);
        lastCleared.put(uuid, System.currentTimeMillis());
        return start == null ? 0 : (System.currentTimeMillis() - start) / 1000;
    }

    public int getTotalAfk() {
        return afkMap.size();
    }
}