package com.ftxeven.aircore.module.gui.invsee;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.player.PlayerInventories;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ViewerListener implements Listener {

    private final AirCore plugin;
    private final InvseeManager invseeManager;

    public ViewerListener(AirCore plugin, InvseeManager invseeManager) {
        this.plugin = plugin;
        this.invseeManager = invseeManager;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeManager.InvseeHolder)) return;

        Player viewer = event.getWhoClicked() instanceof Player p ? p : null;
        if (viewer == null) return;

        if (!viewer.hasPermission("aircore.command.invsee.modify")) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getInventory();
        int topSize = top.getSize();

        ItemStack cursor = event.getOldCursor();
        if (cursor.getType().isAir()) return;

        Set<Integer> rawSlots = event.getRawSlots();

        // If all slots are in the bottom inventory don't process this
        boolean hasTopSlots = rawSlots.stream().anyMatch(slot -> slot < topSize);
        if (!hasTopSlots) return;

        Set<Integer> validSlots = new HashSet<>();
        Set<Integer> invalidSlots = new HashSet<>();

        for (int rawSlot : rawSlots) {
            if (rawSlot >= topSize) {
                // Slots in bottom inventory during a drag
                invalidSlots.add(rawSlot);
                continue;
            }

            boolean registered =
                    InvseeSlotMapper.findItem(invseeManager.definition(), rawSlot) != null;
            boolean dynamic =
                    InvseeSlotMapper.isDynamicSlot(invseeManager.definition(), rawSlot);

            if (!registered || !dynamic) {
                invalidSlots.add(rawSlot);
                continue;
            }

            // Armor slot validation
            var armorSlots = invseeManager.definition().items().get("player-armor").slots();
            if (armorSlots.contains(rawSlot)
                    && !invseeManager.isValidArmorForSlot(cursor, rawSlot, invseeManager.definition())) {
                invalidSlots.add(rawSlot);
                continue;
            }

            validSlots.add(rawSlot);
        }

        if (!invalidSlots.isEmpty()) {
            if (validSlots.isEmpty()) {
                event.setCancelled(true);
                return;
            }

            // Store invalid slot items and their amounts before the drag happens
            Map<Integer, ItemStack> invalidSlotBackup = new HashMap<>();
            for (int slot : invalidSlots) {
                if (slot < topSize) {
                    ItemStack item = top.getItem(slot);
                    invalidSlotBackup.put(slot, item != null ? item.clone() : null);
                } else {
                    invalidSlotBackup.put(slot, null);
                }
            }

            plugin.scheduler().runEntityTask(viewer, () -> {
                int itemsToReturn = calculateItemsInInvalidSlots(top, invalidSlots, invalidSlotBackup, topSize);

                // Restore invalid slots to their original state (only top inventory)
                for (int slot : invalidSlots) {
                    if (slot < topSize) {
                        top.setItem(slot, invalidSlotBackup.get(slot));
                    }
                }

                // Return items to cursor if any were placed on invalid slots
                if (itemsToReturn > 0) {
                    returnItemsToCursor(viewer, cursor, itemsToReturn);
                }

                syncInventory(top, viewer);
            });
        } else {
            // All slots are valid, just sync
            plugin.scheduler().runEntityTask(viewer, () -> syncInventory(top, viewer));
        }
    }

    private int calculateItemsInInvalidSlots(Inventory top, Set<Integer> invalidSlots, Map<Integer, ItemStack> backup, int topSize) {
        int itemsToReturn = 0;

        for (int slot : invalidSlots) {
            if (slot >= topSize) continue; // Skip bottom inventory slots

            ItemStack currentItem = top.getItem(slot);
            ItemStack originalItem = backup.get(slot);

            if (currentItem != null && !currentItem.getType().isAir()) {
                int currentAmount = currentItem.getAmount();
                int originalAmount = (originalItem == null || originalItem.getType().isAir()) ? 0 : originalItem.getAmount();
                int added = Math.max(0, currentAmount - originalAmount);
                itemsToReturn += added;
            }
        }

        return itemsToReturn;
    }

    private void returnItemsToCursor(Player player, ItemStack cursor, int itemsToReturn) {
        ItemStack cursorItem = player.getItemOnCursor();
        if (!cursorItem.getType().isAir() && cursorItem.isSimilar(cursor)) {
            int newAmount = cursorItem.getAmount() + itemsToReturn;
            cursorItem.setAmount(Math.min(newAmount, cursorItem.getMaxStackSize()));
            player.setItemOnCursor(cursorItem);
        } else {
            ItemStack toReturn = cursor.clone();
            toReturn.setAmount(Math.min(itemsToReturn, toReturn.getMaxStackSize()));
            player.setItemOnCursor(toReturn);
        }
    }

    private void syncInventory(Inventory top, Object whoClicked) {
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof InvseeManager.InvseeHolder ih)) return;

        Player player = whoClicked instanceof Player p ? p : null;
        invseeManager.refreshFillers(top, player);

        PlayerInventories.InventoryBundle bundle =
                InvseeSlotMapper.extractBundle(top, invseeManager.definition());

        invseeManager.applyBundleToTarget(ih.targetUUID(), bundle);
    }
}