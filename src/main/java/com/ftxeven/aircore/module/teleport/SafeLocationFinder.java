package com.ftxeven.aircore.module.teleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Optional;

public final class SafeLocationFinder {

    private SafeLocationFinder() {
    }

    public static Optional<Location> findNearestSafe(Location candidate, int radius) {
        if (isSafe(candidate)) {
            return Optional.of(candidate);
        }

        Location best = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        int minY = candidate.getWorld().getMinHeight();
        int maxY = candidate.getWorld().getMaxHeight() - 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    double distanceSquared = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distanceSquared > (double) radius * radius || distanceSquared >= bestDistanceSquared) {
                        continue; // outside the search sphere, or already have a strictly closer match
                    }

                    int blockY = candidate.getBlockY() + dy;
                    if (blockY < minY || blockY > maxY) {
                        continue;
                    }

                    Location test = (dx == 0 && dy == 0 && dz == 0) ? candidate : candidate.clone().add(dx, dy, dz);
                    if (isSafe(test)) {
                        best = test;
                        bestDistanceSquared = distanceSquared;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return isPassable(feet) && isPassable(head) && canLandOn(ground);
    }

    private static boolean isPassable(Block block) {
        Material type = block.getType();
        return !type.isSolid() && !isHarmful(type);
    }

    private static boolean canLandOn(Block block) {
        Material type = block.getType();
        if (isHarmful(type)) {
            return false;
        }
        return type.isSolid() || type == Material.WATER;
    }

    private static boolean isHarmful(Material type) {
        return switch (type) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, POWDER_SNOW, WITHER_ROSE -> true;
            default -> false;
        };
    }
}