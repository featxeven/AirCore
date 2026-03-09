package com.ftxeven.aircore.database.dao;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class PlayerRecords {

    private final AirCore plugin;
    private final Connection connection;

    public record SkinData(String value, String signature) {
        public boolean hasData() { return value != null && !value.isEmpty(); }
    }

    public PlayerRecords(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public void updateJoinInfo(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        var profile = player.getPlayerProfile();
        var textures = profile.getProperties().stream()
                .filter(p -> p.getName().equals("textures"))
                .findFirst().orElse(null);

        String skinValue = textures != null ? textures.getValue() : null;
        String skinSignature = textures != null ? textures.getSignature() : null;

        String sql = """
            UPDATE player_records
            SET name = ?, skin_value = ?, skin_signature = ?, updated_at = ?
            WHERE uuid = ?;
        """;

        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, name);
            ps.setString(2, skinValue);
            ps.setString(3, skinSignature);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.setString(5, uuid.toString());
        });
    }

    public SkinData getSkinData(UUID uuid) {
        String sql = "SELECT skin_value, skin_signature FROM player_records WHERE uuid = ? LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SkinData(
                            rs.getString("skin_value"),
                            rs.getString("skin_signature")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch skin for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public Set<UUID> getAllKnownUuids() {
        Set<UUID> uuids = new HashSet<>();
        String sql = "SELECT uuid FROM player_records;";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                uuids.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch all known UUIDs: " + e.getMessage());
        }
        return uuids;
    }

    public UUID uuidFromName(String name) {
        if (name == null || name.isBlank()) return null;

        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();

        String sql = "SELECT uuid FROM player_records WHERE name = ? COLLATE NOCASE LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch UUID for " + name + ": " + e.getMessage());
        }
        return null;
    }

    public String getName(UUID uuid) {
        if (uuid == null) return null;

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        String sql = "SELECT name FROM player_records WHERE uuid = ? LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch name for UUID " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public String getRealName(String input) {
        if (input == null || input.isBlank()) return input;

        Player online = Bukkit.getPlayer(input);
        if (online != null) return online.getName();

        String sql = "SELECT name FROM player_records WHERE name = ? COLLATE NOCASE LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, input);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException ignored) {}
        return input;
    }

    public void updateName(UUID uuid, String name) {
        String sql = "UPDATE player_records SET name = ?, updated_at = ? WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, name);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
        });
    }

    public int createPlayerRecord(UUID uuid, String name) {
        String sql = """
        INSERT INTO player_records (uuid, name, balance, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            name = excluded.name,
            updated_at = excluded.updated_at;
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, plugin.config().economyDefaultBalance());
            ps.setLong(4, Instant.now().getEpochSecond());

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize player record for " + uuid + ": " + e.getMessage());
        }
        return 0;
    }

    public boolean hasJoinedBefore(UUID uuid) {
        if (plugin.database().isClosed()) return false;

        String sql = "SELECT 1 FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            if (!plugin.database().isClosed()) {
                plugin.getLogger().warning("Failed to check joined-before for " + uuid + ": " + e.getMessage());
            }
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

    public double getWalkSpeed(UUID uuid) {
        String sql = "SELECT walk_speed FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("walk_speed");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch walk_speed: " + e.getMessage());
        }
        return 1.0;
    }

    public double getFlySpeed(UUID uuid) {
        String sql = "SELECT fly_speed FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("fly_speed");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch fly_speed: " + e.getMessage());
        }
        return 1.0;
    }

    public void setSpeed(UUID uuid, String type, double speed) {
        String column = type.equalsIgnoreCase("flying") ? "fly_speed" : "walk_speed";
        String sql = "UPDATE player_records SET " + column + " = ?, updated_at = ? WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> {
            ps.setDouble(1, speed);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
        });
    }

    public boolean getToggle(UUID uuid, ToggleService.Toggle serviceToggle) {
        String column = serviceToggle.getColumn();
        String sql = "SELECT " + column + " FROM player_records WHERE uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(column) != 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch toggle " + column + " for " + uuid + ": " + e.getMessage());
        }
        return serviceToggle.getDefaultValue();
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