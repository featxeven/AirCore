package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class PlayerCooldowns {
    private final AirCore plugin;
    private final Connection connection;

    public PlayerCooldowns(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public Map<String, Long> load(UUID uuid) {
        Map<String, Long> map = new ConcurrentHashMap<>();
        String sql = "SELECT command, expiry FROM player_cooldowns WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("command"), rs.getLong("expiry"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load cooldowns for " + uuid + ": " + e.getMessage());
        }
        return map;
    }

    public void save(UUID uuid, String command, long expiry) {
        String sql = """
            INSERT INTO player_cooldowns (uuid, command, expiry)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, command) DO UPDATE SET expiry = excluded.expiry;
        """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, command);
            ps.setLong(3, expiry);
        });
    }

    public void delete(UUID uuid, String command) {
        String sql = "DELETE FROM player_cooldowns WHERE uuid = ? AND command = ?";
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, command);
        });
    }
}
