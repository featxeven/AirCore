package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;

import java.sql.*;
import java.util.UUID;

public final class PlayerKits {

    private final AirCore plugin;
    private final Connection connection;

    public PlayerKits(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public KitData load(UUID uuid, String kit) {
        String sql = "SELECT last_claim, one_time_claimed, last_cooldown FROM player_kits WHERE uuid = ? AND kit = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long lastClaim = rs.getLong("last_claim");
                    boolean oneTimeClaimed = rs.getInt("one_time_claimed") == 1;
                    long lastCooldown = rs.getLong("last_cooldown");
                    return new KitData(lastClaim, oneTimeClaimed, lastCooldown);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load kit data for " + uuid + "/" + kit + ": " + e.getMessage());
        }
        return new KitData(0, false, 0);
    }

    public void save(UUID uuid, String kit, long lastClaim, boolean oneTimeClaimed, long cooldown) {
        String sql = """
            INSERT INTO player_kits (uuid, kit, last_claim, one_time_claimed, last_cooldown)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid, kit) DO UPDATE SET
                last_claim = excluded.last_claim,
                one_time_claimed = excluded.one_time_claimed,
                last_cooldown = excluded.last_cooldown;
        """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setLong(3, lastClaim);
            ps.setInt(4, oneTimeClaimed ? 1 : 0);
            ps.setLong(5, cooldown);
        });
    }

    public record KitData(long lastClaim, boolean oneTimeClaimed, long lastCooldown) {}
}
