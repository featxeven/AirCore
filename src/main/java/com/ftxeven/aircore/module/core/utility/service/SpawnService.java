package com.ftxeven.aircore.module.core.utility.service;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class SpawnService {

    private final AirCore plugin;
    private final File file;
    private final YamlConfiguration config;

    public SpawnService(AirCore plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataFolder.getAbsolutePath());
        }

        this.file = new File(dataFolder, "spawn.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveSpawn(Location loc) {
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save spawn.yml: " + e.getMessage());
        }
    }

    public Location loadSpawn() {
        String worldName = config.getString("spawn.world");
        if (worldName == null || worldName.isBlank()) return null;

        var world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}