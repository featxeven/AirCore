package com.ftxeven.aircore.core.utility.service;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class WarpService {

    private final AirCore plugin;
    private final File file;
    private final YamlConfiguration config;

    public WarpService(AirCore plugin) {
        this.plugin = plugin;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataFolder.getAbsolutePath());
        }

        this.file = new File(dataFolder, "warps.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveWarp(String name, Location loc) {
        String path = "warps." + name.toLowerCase();
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save warps.yml: " + e.getMessage());
        }
    }

    public Location loadWarp(String name) {
        String path = "warps." + name.toLowerCase();
        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) return null;

        var world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean deleteWarp(String name) {
        String path = "warps." + name.toLowerCase();
        if (!config.contains(path)) {
            return false;
        }
        config.set(path, null);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save warps.yml after deleting warp: " + e.getMessage());
        }
        return true;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public int getTotalWarps() {
        var section = config.getConfigurationSection("warps");
        return section != null ? section.getKeys(false).size() : 0;
    }

    public boolean hasPermission(UUID uuid, String warpName) {
        var player = plugin.getServer().getPlayer(uuid);
        if (player == null) return false;
        return player.hasPermission("aircore.command.warp." + warpName.toLowerCase());
    }

    public boolean exists(String warpName) {
        if (warpName == null || warpName.isBlank()) return false;
        return config.contains("warps." + warpName.toLowerCase());
    }
}
