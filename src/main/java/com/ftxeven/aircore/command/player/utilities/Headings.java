package com.ftxeven.aircore.command.player.utilities;

// Shared by /compass and /near
final class Headings {

    private static final String[] KEYS = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"
    };

    private Headings() {
    }

    static double bearing(float yaw) {
        return normalize(yaw + 180.0);
    }

    static double bearingTo(double dx, double dz) {
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    static String key(double bearing) {
        return KEYS[(int) Math.round(bearing / 45.0) % KEYS.length];
    }

    private static double normalize(double degrees) {
        return (degrees % 360.0 + 360.0) % 360.0;
    }
}