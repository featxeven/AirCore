package com.ftxeven.aircore.module;

import com.ftxeven.aircore.model.Position;
import org.bukkit.Location;

public final class StoredLocations {

    private StoredLocations() {
    }

    public sealed interface Resolution {
        record Ready(Location location) implements Resolution {}
        record WorldMissing(String world) implements Resolution {}
    }

    public static Resolution resolve(Position position) {
        Location location = Positions.toLocation(position);
        return location != null ? new Resolution.Ready(location) : new Resolution.WorldMissing(position.world());
    }
}