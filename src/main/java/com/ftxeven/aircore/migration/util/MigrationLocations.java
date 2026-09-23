package com.ftxeven.aircore.migration.util;

import com.ftxeven.aircore.model.Position;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class MigrationLocations {

    private MigrationLocations() {
    }

    public static Optional<Position> read(@Nullable ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }

        String world = worldName(firstSet(section, "world-name", "world"));
        if (world == null || !section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            return Optional.empty();
        }

        return Optional.of(new Position(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch")));
    }

    private static @Nullable String firstSet(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // essentials sometimes stores the world's uuid instead of its name
    private static @Nullable String worldName(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(UUID.fromString(raw));
            return world != null ? world.getName() : raw;
        } catch (Exception e) {
            return raw;
        }
    }
}