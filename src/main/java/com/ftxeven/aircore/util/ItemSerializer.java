package com.ftxeven.aircore.util;

import org.bukkit.inventory.ItemStack;

import java.io.*;

public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static byte[] serialize(ItemStack item) {
        return item.serializeAsBytes();
    }

    public static ItemStack deserialize(byte[] data) {
        return ItemStack.deserializeBytes(data);
    }

    public static byte[] serializeAll(ItemStack[] items) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(-1);
                    continue;
                }
                byte[] bytes = serialize(item);
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize items", e);
        }
    }

    public static ItemStack[] deserializeAll(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            ItemStack[] items = new ItemStack[in.readInt()];
            for (int i = 0; i < items.length; i++) {
                int length = in.readInt();
                if (length < 0) {
                    continue;
                }
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                items[i] = deserialize(bytes);
            }
            return items;
        } catch (IOException e) {
            throw new IllegalStateException("Could not deserialize items", e);
        }
    }
}