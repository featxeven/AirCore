package com.ftxeven.aircore.module;

import com.ftxeven.aircore.model.Position;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public final class Positions {

    private Positions() {
    }

    public static Position of(Location location) {
        return new Position(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    // null if the position's world isn't currently loaded
    public static @Nullable Location toLocation(Position position) {
        World world = Bukkit.getWorld(position.world());
        return world == null ? null : new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    public static double distanceSquared(Position a, Position b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public static Location toBlockCenter(Location location) {
        Location centered = location.clone();
        centered.setX(Math.floor(location.getX()) + 0.5);
        centered.setZ(Math.floor(location.getZ()) + 0.5);
        return centered;
    }
}