package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;

import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerBlocks {
    private final AirCore plugin;
    private final Connection connection;

    public PlayerBlocks(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public Set<UUID> load(UUID uuid) {
        Set<UUID> blocked = new HashSet<>();
        String sql = "SELECT blocked_uuid FROM player_blocks WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    blocked.add(UUID.fromString(rs.getString("blocked_uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load blocks for " + uuid + ": " + e.getMessage());
        }
        return blocked;
    }

    public void add(UUID uuid, UUID target) {
        String sql = """
            INSERT INTO player_blocks (uuid, blocked_uuid, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, blocked_uuid) DO NOTHING;
            """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, target.toString());
            ps.setLong(3, Instant.now().getEpochSecond());
        });
    }

    public void remove(UUID uuid, UUID target) {
        String sql = "DELETE FROM player_blocks WHERE uuid = ? AND blocked_uuid = ?";
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, target.toString());
        });
    }
}
