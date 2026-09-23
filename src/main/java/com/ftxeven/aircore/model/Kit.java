package com.ftxeven.aircore.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Kit(
        String name,
        ItemStack[] items,
        boolean oneTime,
        @Nullable Integer cooldownSeconds,
        boolean dropOnFullInventory,
        boolean exactSlots,
        boolean requiresPermission,
        Instant createdAt,
        @Nullable UUID createdBy
) {
    public static final int MAIN_SIZE = 36;
    public static final int OFFHAND_SLOT = 36;
    public static final int BOOTS_SLOT = 37;
    public static final int LEGGINGS_SLOT = 38;
    public static final int CHESTPLATE_SLOT = 39;
    public static final int HELMET_SLOT = 40;
    public static final int TOTAL_SLOTS = 41;

    public Kit {
        items = normalized(items);
    }

    private static ItemStack[] normalized(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < Math.min(items.length, TOTAL_SLOTS); i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) {
                copy[i] = item.clone();
            }
        }
        return copy;
    }

    public ItemStack[] nonEmptyItems() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                list.add(item);
            }
        }
        return list.toArray(new ItemStack[0]);
    }

    public int itemCount() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) {
                count++;
            }
        }
        return count;
    }
}