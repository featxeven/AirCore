package com.ftxeven.aircore.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemDelivery {

    private ItemDelivery() {}

    public enum Result { DELIVERED, DROPPED, REJECTED }

    public record BulkResult(Result result, int requiredSlots) {}

    public static boolean fits(PlayerInventory inventory, ItemStack item, int amount) {
        int maxStackSize = item.getMaxStackSize();
        int capacity = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                capacity += maxStackSize;
            } else if (stack.isSimilar(item)) {
                capacity += maxStackSize - stack.getAmount();
            }
            if (capacity >= amount) {
                return true;
            }
        }
        return false;
    }

    public static boolean rejects(Player player, ItemStack item, int amount, boolean dropOnFullInventory) {
        return !dropOnFullInventory && !fits(player.getInventory(), item, amount);
    }

    public static Result give(Player player, ItemStack item, int amount, boolean dropOnFullInventory) {
        if (rejects(player, item, amount, dropOnFullInventory)) {
            return Result.REJECTED;
        }
        ItemStack stack = item.clone();
        stack.setAmount(amount);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return Result.DELIVERED;
        }
        for (ItemStack remaining : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
        return Result.DROPPED;
    }

    public static BulkResult giveAll(Player player, ItemStack[] items, boolean dropOnFullInventory) {
        if (!dropOnFullInventory) {
            int shortage = shortageSlots(player.getInventory().getStorageContents(), items);
            if (shortage > 0) {
                return new BulkResult(Result.REJECTED, shortage);
            }
        }

        boolean droppedAny = false;
        for (ItemStack item : items) {
            if (give(player, item, item.getAmount(), true) == Result.DROPPED) {
                droppedAny = true;
            }
        }
        return new BulkResult(droppedAny ? Result.DROPPED : Result.DELIVERED, 0);
    }

    public static int shortageSlots(ItemStack[] storageSnapshot, ItemStack[] items) {
        List<ItemStack> simulated = new ArrayList<>();
        int freeSlots = 0;
        for (ItemStack stack : storageSnapshot) {
            if (stack == null || stack.getType().isAir()) {
                freeSlots++;
            } else {
                simulated.add(stack.clone());
            }
        }

        int shortage = 0;
        for (ItemStack item : items) {
            int remaining = item.getAmount();
            for (ItemStack existing : simulated) {
                if (remaining <= 0) break;
                if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                    int used = Math.min(remaining, existing.getMaxStackSize() - existing.getAmount());
                    existing.setAmount(existing.getAmount() + used);
                    remaining -= used;
                }
            }
            while (remaining > 0) {
                int placed = Math.min(remaining, item.getMaxStackSize());
                if (freeSlots > 0) {
                    freeSlots--;
                    ItemStack placedStack = item.clone();
                    placedStack.setAmount(placed);
                    simulated.add(placedStack);
                } else {
                    shortage++;
                }
                remaining -= placed;
            }
        }
        return shortage;
    }
}