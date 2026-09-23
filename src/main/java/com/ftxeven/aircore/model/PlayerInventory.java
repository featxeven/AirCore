package com.ftxeven.aircore.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record PlayerInventory(UUID uuid, ItemStack[] contents, ItemStack[] enderChest, int heldSlot) {

    public static final int MAIN_SIZE = 41; // 36 storage + 4 armor + 1 offhand
    public static final int ENDERCHEST_SIZE = 54; // full 6-row buffer

    public PlayerInventory {
        contents = contents.clone();
        enderChest = enderChest.clone();
    }

    public static PlayerInventory empty(UUID uuid) {
        return new PlayerInventory(uuid, new ItemStack[MAIN_SIZE], new ItemStack[ENDERCHEST_SIZE], 0);
    }
}