package com.ftxeven.aircore.module.core.utility.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkService {
    private final Map<UUID, Long> afkMap = new ConcurrentHashMap<>();

    public void setAfk(UUID uuid) {
        afkMap.put(uuid, System.currentTimeMillis());
    }

    public boolean isAfk(UUID uuid) {
        return afkMap.containsKey(uuid);
    }

    public long clearAfk(UUID uuid) {
        Long start = afkMap.remove(uuid);
        return start == null ? 0 : (System.currentTimeMillis() - start) / 1000;
    }

    public int getTotalAfk() {
        return afkMap.size();
    }
}
