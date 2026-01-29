package com.ftxeven.aircore.module.core.utility.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackService {
    private final Map<UUID, Location> lastDeathMap = new ConcurrentHashMap<>();

    public void setLastDeath(UUID uuid, Location location) {
        if (uuid != null && location != null) {
            lastDeathMap.put(uuid, location.clone());
        }
    }

    public Location getLastDeath(UUID uuid) {
        return lastDeathMap.get(uuid);
    }

    public void clearLastDeath(UUID uuid) {
        lastDeathMap.remove(uuid);
    }
}
