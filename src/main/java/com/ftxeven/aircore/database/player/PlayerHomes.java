package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class PlayerHomes {
    private final AirCore plugin;
    private final Connection connection;

    public PlayerHomes(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public Map<String, Location> load(UUID uuid) {
        Map<String, Location> homes = new HashMap<>();
        String sql = "SELECT name, world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue; // skip invalid worlds
                    Location loc = new Location(
                            world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                    homes.put(rs.getString("name"), loc);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load homes for " + uuid + ": " + e.getMessage());
        }
        return homes;
    }

    public void save(UUID uuid, String name, Location loc) {
        String sql = """
            INSERT INTO player_homes (uuid, name, world, x, y, z, yaw, pitch, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid, name) DO UPDATE SET
                world = excluded.world,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                yaw = excluded.yaw,
                pitch = excluded.pitch,
                created_at = excluded.created_at;
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.setLong(9, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save home '" + name + "' for " + uuid + ": " + e.getMessage());
        }
    }

    public void delete(UUID uuid, String name) {
        String sql = "DELETE FROM player_homes WHERE uuid = ? AND name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete home '" + name + "' for " + uuid + ": " + e.getMessage());
        }
    }

    public int getHomeAmount(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM player_homes WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count homes for " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    public List<String> getHomeNames(UUID uuid) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM player_homes WHERE uuid = ? ORDER BY created_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load home names for " + uuid + ": " + e.getMessage());
        }
        return names;
    }

    public int getHomeLimit(UUID uuid) {
        int baseLimit = plugin.config().homesMaxHomes();

        var player = Bukkit.getPlayer(uuid);
        if (player != null) {
            for (var perm : player.getEffectivePermissions()) {
                String node = perm.getPermission();
                if (node.startsWith("aircore.bypass.home.limit.")) {
                    String value = node.substring("aircore.bypass.home.limit.".length());
                    try {
                        int parsed = Integer.parseInt(value);
                        baseLimit = Math.max(baseLimit, parsed);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return baseLimit;
    }
}