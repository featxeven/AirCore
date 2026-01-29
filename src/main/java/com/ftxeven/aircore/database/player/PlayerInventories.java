package com.ftxeven.aircore.database.player;

import com.ftxeven.aircore.AirCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.sql.*;
import java.util.Collection;
import java.util.UUID;

public final class PlayerInventories {
    private final AirCore plugin;
    private final Connection connection;

    public static final int CONTENTS_SIZE = 36;
    public static final int ARMOR_SIZE = 4;
    public static final int OFFHAND_SIZE = 1;
    public static final int ENDERCHEST_SIZE = 27;

    public PlayerInventories(AirCore plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    private byte[] serializeItems(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(0); // mark empty slot
                } else {
                    byte[] data = item.serializeAsBytes();
                    out.writeInt(data.length);
                    out.write(data);
                }
            }
            return baos.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to serialize items: " + e.getMessage());
            return new byte[0];
        }
    }

    private ItemStack[] deserializeItems(byte[] blob, int expectedSlots) {
        ItemStack[] items = new ItemStack[expectedSlots];
        if (blob == null || blob.length == 0) return items;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(blob);
             DataInputStream in = new DataInputStream(bais)) {

            int len = in.readInt();
            for (int i = 0; i < Math.min(len, expectedSlots); i++) {
                int size = in.readInt();
                if (size > 0) {
                    byte[] data = new byte[size];
                    in.readFully(data);
                    items[i] = ItemStack.deserializeBytes(data);
                } else {
                    items[i] = null; // empty slot
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize items: " + e.getMessage());
        }
        return items;
    }

    public void saveAllSync(Collection<? extends Player> players) {
        String sql = """
            INSERT INTO player_inventories (uuid, contents, armor, offhand, enderchest)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                contents = excluded.contents,
                armor = excluded.armor,
                offhand = excluded.offhand,
                enderchest = excluded.enderchest;
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            boolean prevAuto = connection.getAutoCommit();
            connection.setAutoCommit(false);

            for (Player player : players) {
                UUID uuid = player.getUniqueId();
                ps.setString(1, uuid.toString());
                ps.setBytes(2, serializeItems(player.getInventory().getContents()));
                ps.setBytes(3, serializeItems(player.getInventory().getArmorContents()));
                ps.setBytes(4, serializeItems(new ItemStack[]{player.getInventory().getItemInOffHand()}));
                ps.setBytes(5, serializeItems(player.getEnderChest().getContents()));
                ps.addBatch();
            }

            ps.executeBatch();
            connection.commit();
            connection.setAutoCommit(prevAuto);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to batch-save inventories: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().warning("Rollback failed: " + rollbackEx.getMessage());
            }
        }
    }

    public void saveInventory(UUID uuid, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        String sql = """
            INSERT INTO player_inventories (uuid, contents, armor, offhand)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                contents = excluded.contents,
                armor = excluded.armor,
                offhand = excluded.offhand;
        """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setBytes(2, serializeItems(contents));
            ps.setBytes(3, serializeItems(armor));
            ps.setBytes(4, serializeItems(new ItemStack[]{offhand}));
        });
    }

    public void saveEnderchest(UUID uuid, ItemStack[] enderChest) {
        String sql = """
            INSERT INTO player_inventories (uuid, enderchest)
            VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                enderchest = excluded.enderchest;
        """;
        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setBytes(2, serializeItems(enderChest));
        });
    }

    public record InventoryBundle(ItemStack[] contents,
                                  ItemStack[] armor,
                                  ItemStack offhand,
                                  ItemStack[] enderChest) {}

    public InventoryBundle loadAllInventory(UUID uuid) {
        String sql = "SELECT contents, armor, offhand, enderchest FROM player_inventories WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                ItemStack[] contents = deserializeItems(rs.getBytes("contents"), CONTENTS_SIZE);
                ItemStack[] armor = deserializeItems(rs.getBytes("armor"), ARMOR_SIZE);
                ItemStack[] offhandArr = deserializeItems(rs.getBytes("offhand"), OFFHAND_SIZE);
                ItemStack offhand = (offhandArr.length > 0) ? offhandArr[0] : null;
                ItemStack[] enderChest = deserializeItems(rs.getBytes("enderchest"), ENDERCHEST_SIZE);

                return new InventoryBundle(contents, armor, offhand, enderChest);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load full inventory for " + uuid + ": " + e.getMessage());
            return null;
        }
    }
}
