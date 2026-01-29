package com.ftxeven.aircore.module.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SellListener implements Listener {

    private final AirCore plugin;
    private final SellManager manager;

    public SellListener(AirCore plugin, SellManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellManager.SellHolder)) return;

        event.getClickedInventory();

        plugin.scheduler().runEntityTask(viewer, () ->
                refreshConfirmButton(viewer, top)
        );
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player viewer)) return;

        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) {
            plugin.scheduler().runEntityTask(viewer, () ->
                    refreshConfirmButton(viewer, top)
            );

        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player viewer = event.getPlayer();
        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) {
            plugin.scheduler().runEntityTask(viewer, () ->
                    refreshConfirmButton(viewer, top)
            );

        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellManager.SellHolder)) return;

        Player player = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        var sellSlots = manager.definition().items().get("sell-slots").slots();

        if (plugin.gui().isReloading()) {
            returnItemsToPlayer(player, inv, sellSlots);
            return;
        }

        if (manager.consumeProcessedSale(player.getUniqueId())) {
            return;
        }

        boolean sellOnClose = manager.definition().getBoolean("sell-on-inventory-close", false);

        if (sellOnClose) {
            SellSlotMapper.WorthResult result =
                    SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), manager.definition());
            double total = result.total();

            if (result.hasUnsupported()) {
                MessageUtil.send(player, "sell-failed", Map.of());
                returnItemsToPlayer(player, inv, sellSlots);
                return;
            }

            if (total <= 0) {
                MessageUtil.send(player, "cannot-sell-air", Map.of());
                returnItemsToPlayer(player, inv, sellSlots);
                return;
            }

            double toDeposit = plugin.economy().formats().round(total);
            String formatted = plugin.economy().formats().formatAmount(toDeposit);

            manager.executeConfirmActions(player, formatted);

            var econResult = plugin.economy().transactions().deposit(player.getUniqueId(), toDeposit);

            if (econResult.type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(player, "sell-success", Map.of("amount", formatted));

                if (manager.definition().getBoolean("sell-logs-on-console", true)) {
                    plugin.getLogger().info(player.getName() + " sold items for " + formatted);
                }

                for (int slot : sellSlots) {
                    if (slot < inv.getSize()) {
                        inv.setItem(slot, null);
                    }
                }
            } else {
                MessageUtil.send(player, "sell-failed", Map.of());
                returnItemsToPlayer(player, inv, sellSlots);
            }
        } else {
            returnItemsToPlayer(player, inv, sellSlots);
        }
    }

    private void returnItemsToPlayer(Player player, Inventory inv, java.util.List<Integer> sellSlots) {
        for (int slot : sellSlots) {
            if (slot >= inv.getSize()) continue;

            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            // Try to add to player inventory
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            // If inventory is full, drop on ground
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }

            inv.setItem(slot, null);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellManager.SellHolder)) return;

        Player viewer = event.getWhoClicked() instanceof Player p ? p : null;
        if (viewer == null) return;

        Inventory top = event.getInventory();
        int topSize = top.getSize();

        ItemStack cursor = event.getOldCursor();
        if (cursor.getType().isAir()) return;

        Set<Integer> rawSlots = event.getRawSlots();
        boolean hasTopSlots = false;
        for (int slot : rawSlots) {
            if (slot < topSize) {
                hasTopSlots = true;
                break;
            }
        }
        if (!hasTopSlots) return;

        var sellSlots = manager.definition().items().get("sell-slots").slots();
        var confirmItem = manager.definition().items().get("confirm");
        var confirmSlots = confirmItem != null ? confirmItem.slots() : java.util.List.<Integer>of();

        Set<Integer> validSlots = new HashSet<>();
        Set<Integer> invalidSlots = new HashSet<>();

        for (int rawSlot : rawSlots) {
            if (rawSlot >= topSize) {
                invalidSlots.add(rawSlot);
                continue;
            }
            if (isSlotInList(rawSlot, sellSlots) || isSlotInList(rawSlot, confirmSlots)) {
                validSlots.add(rawSlot);
            } else {
                invalidSlots.add(rawSlot);
            }
        }

        if (!invalidSlots.isEmpty()) {
            if (validSlots.isEmpty()) {
                event.setCancelled(true);
                return;
            }

            Map<Integer, ItemStack> backup = new HashMap<>(invalidSlots.size());
            for (int slot : invalidSlots) {
                if (slot < topSize) {
                    ItemStack item = top.getItem(slot);
                    backup.put(slot, item != null ? item.clone() : null);
                } else {
                    backup.put(slot, null);
                }
            }

            plugin.scheduler().runEntityTask(viewer, () -> {
                int itemsToReturn = calculateItemsInInvalidSlots(top, invalidSlots, backup, topSize);
                for (int slot : invalidSlots) {
                    if (slot < topSize) {
                        top.setItem(slot, backup.get(slot));
                    }
                }
                if (itemsToReturn > 0) {
                    returnItemsToCursor(viewer, cursor, itemsToReturn);
                }
                refreshConfirmButton(viewer, top);
            });
        } else {
            // All slots are valid, refresh after drag completes
            plugin.scheduler().runEntityTaskDelayed(viewer,
                    () -> refreshConfirmButton(viewer, top),
                    1L
            );
        }
    }

    private boolean isSlotInList(int slot, java.util.List<Integer> list) {
        for (int s : list) {
            if (s == slot) return true;
        }
        return false;
    }

    private int calculateItemsInInvalidSlots(Inventory top, Set<Integer> invalidSlots,
                                             Map<Integer, ItemStack> backup, int topSize) {
        int itemsToReturn = 0;
        for (int slot : invalidSlots) {
            if (slot >= topSize) continue;
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

    private void refreshConfirmButton(Player viewer, Inventory inv) {
        var confirmItem = manager.definition().items().get("confirm");
        if (confirmItem != null) {
            SellSlotMapper.WorthResult result =
                    SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), manager.definition());
            double total = result.total();
            SellSlotMapper.updateConfirmButton(
                    confirmItem,
                    inv,
                    viewer,
                    total,
                    Map.of("player", viewer.getName()),
                    plugin.economy().formats()
            );
        }

        var confirmAllItem = manager.definition().items().get("confirm-all");
        if (confirmAllItem != null) {
            double totalAll = SellSlotMapper.calculateWorthAll(inv, plugin.economy().worth(), manager.definition(), viewer);
            SellSlotMapper.updateConfirmButton(
                    confirmAllItem,
                    inv,
                    viewer,
                    totalAll,
                    Map.of("player", viewer.getName()),
                    plugin.economy().formats()
            );
        }
    }
}