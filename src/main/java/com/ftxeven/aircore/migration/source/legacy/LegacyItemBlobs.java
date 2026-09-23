package com.ftxeven.aircore.migration.source.legacy;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

final class LegacyItemBlobs {

    private static final int MAX_SLOTS = 1024;

    private LegacyItemBlobs() {
    }

    static ItemStack[] decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new ItemStack[0];
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int length = in.readInt();
            if (length <= 0 || length > MAX_SLOTS) {
                return new ItemStack[0];
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = in.readInt();
                if (size <= 0) {
                    continue;
                }
                byte[] data = new byte[size];
                in.readFully(data);
                try {
                    items[i] = ItemStack.deserializeBytes(data);
                } catch (Exception ignored) {
                    // one unreadable item shouldn't cost the player the rest of the slot's row
                }
            }
            return items;
        } catch (IOException e) {
            return new ItemStack[0];
        }
    }

    static void copy(ItemStack[] from, int fromIndex, ItemStack[] to, int toIndex, int count) {
        for (int i = 0; i < count; i++) {
            int source = fromIndex + i;
            int destination = toIndex + i;
            if (source >= from.length || destination >= to.length) {
                return;
            }
            to[destination] = from[source];
        }
    }
}