package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.KitRepository;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlKitRepository implements KitRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlKitRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "kits";
    }

    @Override
    public Optional<Kit> find(String name) {
        String sql = "SELECT * FROM " + table + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapKit(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load kit " + name, e);
        }
    }

    @Override
    public List<Kit> findAll() {
        String sql = "SELECT * FROM " + table + " ORDER BY name ASC";
        List<Kit> kits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                kits.add(mapKit(result));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load kits", e);
        }
        return kits;
    }

    @Override
    public void save(Kit kit) {
        byte[] serialized = ItemSerializer.serializeAll(kit.items());
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET items = ?, one_time = ?, cooldown_seconds = ?, drop_on_full_inventory = ?, exact_slots = ?, requires_permission = ? WHERE name = ?")) {
                update.setBytes(1, serialized);
                update.setBoolean(2, kit.oneTime());
                bindCooldown(update, 3, kit.cooldownSeconds());
                update.setBoolean(4, kit.dropOnFullInventory());
                update.setBoolean(5, kit.exactSlots());
                update.setBoolean(6, kit.requiresPermission());
                update.setString(7, kit.name());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (name, items, one_time, cooldown_seconds, drop_on_full_inventory, exact_slots, requires_permission, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, kit.name());
                insert.setBytes(2, serialized);
                insert.setBoolean(3, kit.oneTime());
                bindCooldown(insert, 4, kit.cooldownSeconds());
                insert.setBoolean(5, kit.dropOnFullInventory());
                insert.setBoolean(6, kit.exactSlots());
                insert.setBoolean(7, kit.requiresPermission());
                insert.setLong(8, kit.createdAt().toEpochMilli());
                insert.setString(9, kit.createdBy() != null ? kit.createdBy().toString() : null);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save kit " + kit.name(), e);
        }
    }

    @Override
    public boolean delete(String name) {
        String sql = "DELETE FROM " + table + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete kit " + name, e);
        }
    }

    private void bindCooldown(PreparedStatement statement, int index, Integer cooldownSeconds) throws SQLException {
        if (cooldownSeconds != null) {
            statement.setInt(index, cooldownSeconds);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private Kit mapKit(ResultSet rs) throws SQLException {
        String createdBy = rs.getString("created_by");
        ItemStack[] items = ItemSerializer.deserializeAll(rs.getBytes("items"));
        Integer cooldown = getNullableInt(rs, "cooldown_seconds");
        return new Kit(
                rs.getString("name"),
                items,
                rs.getBoolean("one_time"),
                cooldown,
                rs.getBoolean("drop_on_full_inventory"),
                rs.getBoolean("exact_slots"),
                rs.getBoolean("requires_permission"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                createdBy != null ? UUID.fromString(createdBy) : null);
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}