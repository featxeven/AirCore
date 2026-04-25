package com.ftxeven.aircore.core.module.utility.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackService {

    private final Map<UUID, Location> lastLocationMap = new ConcurrentHashMap<>();

    public void setLastLocation(UUID uuid, Location location) {
        if (uuid != null && location != null) {
            lastLocationMap.put(uuid, location.clone());
        }
    }

    public Location getLastLocation(UUID uuid) {
        return lastLocationMap.get(uuid);
    }

    public void clearLastLocation(UUID uuid) {
        lastLocationMap.remove(uuid);
    }
}