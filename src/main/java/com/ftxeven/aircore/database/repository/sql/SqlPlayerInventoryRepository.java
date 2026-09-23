package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.PlayerInventoryRepository;
import com.ftxeven.aircore.model.PlayerInventory;
import com.ftxeven.aircore.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class SqlPlayerInventoryRepository implements PlayerInventoryRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlPlayerInventoryRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "player_inventories";
    }

    @Override
    public Optional<PlayerInventory> find(UUID uuid) {
        String sql = "SELECT * FROM " + table + " WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerInventory(
                        uuid,
                        ItemSerializer.deserializeAll(result.getBytes("contents")),
                        ItemSerializer.deserializeAll(result.getBytes("ender_chest")),
                        result.getInt("held_slot")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load inventory for player " + uuid, e);
        }
    }

    @Override
    public void saveContents(UUID uuid, ItemStack[] contents, int heldSlot) {
        byte[] serialized = ItemSerializer.serializeAll(contents);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET contents = ?, held_slot = ? WHERE uuid = ?")) {
                update.setBytes(1, serialized);
                update.setInt(2, heldSlot);
                update.setString(3, uuid.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (uuid, contents, held_slot, ender_chest) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setBytes(2, serialized);
                insert.setInt(3, heldSlot);
                insert.setBytes(4, ItemSerializer.serializeAll(new ItemStack[PlayerInventory.ENDERCHEST_SIZE]));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save inventory contents for player " + uuid, e);
        }
    }

    @Override
    public void saveEnderChest(UUID uuid, ItemStack[] enderChest) {
        byte[] serialized = ItemSerializer.serializeAll(enderChest);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET ender_chest = ? WHERE uuid = ?")) {
                update.setBytes(1, serialized);
                update.setString(2, uuid.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (uuid, contents, held_slot, ender_chest) VALUES (?, ?, 0, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setBytes(2, ItemSerializer.serializeAll(new ItemStack[PlayerInventory.MAIN_SIZE]));
                insert.setBytes(3, serialized);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save enderchest for player " + uuid, e);
        }
    }

    @Override
    public void delete(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete inventory for player " + uuid, e);
        }
    }
}