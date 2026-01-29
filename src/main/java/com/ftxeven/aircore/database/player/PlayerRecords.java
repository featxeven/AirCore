package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class PlayerRecords {

    private final AirCore plugin;
    private final Connection connection;

    public PlayerRecords(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public UUID uuidFromName(String name) {
        if (name == null || name.isBlank()) return null;
        Map<String, UUID> cache = plugin.getNameCache();
        return cache.get(name.toLowerCase());
    }

    public int createPlayerRecord(UUID uuid, String name) {
        int nextIndex = getMaxJoinIndex() + 1;
        String sql = """
            INSERT INTO player_records (join_index, uuid, name, balance, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO NOTHING;
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, nextIndex);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setDouble(4, plugin.config().economyDefaultBalance());
            ps.setLong(5, Instant.now().getEpochSecond());
            ps.executeUpdate();
            return nextIndex;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize player record for " + uuid + ": " + e.getMessage());
        }
        return 0;
    }

    public Map<String, UUID> loadAllNames() {
        Map<String, UUID> cache = new HashMap<>();
        String sql = "SELECT uuid, name FROM player_records;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID u = UUID.fromString(rs.getString("uuid"));
                String n = rs.getString("name");
                if (n != null) cache.put(n.toLowerCase(), u);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player names: " + e.getMessage());
        }
        return cache;
    }

    public boolean hasJoinedBefore(UUID uuid) {
        String sql = "SELECT 1 FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check joined-before for " + uuid + ": " + e.getMessage());
        }
        return false;
    }

    public Integer getJoinIndex(UUID uuid) {
        String sql = "SELECT join_index FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("join_index");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch join_index for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public int getMaxJoinIndex() {
        String sql = "SELECT MAX(join_index) AS max_index FROM player_records;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("max_index");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch max join_index: " + e.getMessage());
        }
        return 0;
    }

    public double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch balance for " + uuid + ": " + e.getMessage());
        }
        return plugin.config().economyDefaultBalance();
    }

    public void setBalance(UUID uuid, double amount) {
        String sql = "UPDATE player_records SET balance = ?, updated_at = ? WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> {
            ps.setDouble(1, amount);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
        });
    }

    public double getSpeed(UUID uuid) {
        String sql = "SELECT speed FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("speed");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch speed for " + uuid + ": " + e.getMessage());
        }
        return 1.0;
    }

    public void setSpeed(UUID uuid, double speed) {
        String sql = "UPDATE player_records SET speed = ?, updated_at = ? WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> {
            ps.setDouble(1, speed);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
        });
    }

    public boolean getToggle(UUID uuid, String column) {
        String sql = "SELECT " + column + " FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(column) != 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch toggle " + column + " for " + uuid + ": " + e.getMessage());
        }
        return true; // default
    }

    public void setToggle(UUID uuid, String column, boolean value) {
        String sql = "UPDATE player_records SET " + column + " = ?, updated_at = ? WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> {
            ps.setInt(1, value ? 1 : 0);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
        });
    }

    public Location getLocation(UUID uuid) {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) return null;
                return new Location(world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch location for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public void setLocation(UUID uuid, Location loc) {
        String sql = """
                UPDATE player_records
                SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, updated_at = ?
                WHERE uuid = ?;
            """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.setLong(7, Instant.now().getEpochSecond());
            ps.setString(8, uuid.toString());
        });
    }
}