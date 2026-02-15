package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class SellListener implements Listener {

    private final AirCore plugin;
    private final SellManager manager;
    private final Set<UUID> pendingUpdates = new HashSet<>();

    public SellListener(AirCore plugin, SellManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void debounceRefresh(Player viewer, Inventory inv) {
        if (!pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                if (inv.getHolder() instanceof SellManager.SellHolder) {
                    manager.refreshConfirmButton(inv, viewer);
                }
            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) {
            debounceRefresh(viewer, top);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellManager.SellHolder)) return;

        Player player = (Player) e.getPlayer();

        if (manager.isTransitioning(player.getUniqueId())) {
            return;
        }

        Inventory inv = e.getInventory();
        var sellSlots = manager.definition().items().get("sell-slots").slots();

        if (plugin.gui().isReloading() || manager.consumeProcessedSale(player.getUniqueId())) {
            returnItemsToPlayer(player, inv, sellSlots);
            return;
        }

        boolean sellOnClose = manager.definition().config().getBoolean("sell-on-inventory-close", false);
        if (sellOnClose) {
            SellSlotMapper.WorthResult result = SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), manager.definition());

            if (result.hasUnsupported()) {
                MessageUtil.send(player, "economy.sell.error-failed", Map.of());
                returnItemsToPlayer(player, inv, sellSlots);
                return;
            }

            double total = result.total();
            if (total <= 0) {
                boolean hasItems = false;
                for (int slot : sellSlots) {
                    ItemStack item = inv.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        hasItems = true;
                        break;
                    }
                }
                if (hasItems) MessageUtil.send(player, "economy.sell.error-invalid", Map.of());

                returnItemsToPlayer(player, inv, sellSlots);
                return;
            }

            double toDeposit = plugin.economy().formats().round(total);
            String formatted = plugin.economy().formats().formatAmount(toDeposit);

            var econResult = plugin.economy().transactions().deposit(player.getUniqueId(), toDeposit);

            if (econResult.type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(player, "economy.sell.success", Map.of("amount", formatted));

                if (manager.definition().config().getBoolean("sell-logs-on-console", false)) {
                    plugin.getLogger().info(player.getName() + " sold items for $" + formatted);
                }

                for (int slot : sellSlots) if (slot < inv.getSize()) inv.setItem(slot, null);
            } else {
                MessageUtil.send(player, "economy.sell.error-failed", Map.of());
                returnItemsToPlayer(player, inv, sellSlots);
            }
        } else {
            returnItemsToPlayer(player, inv, sellSlots);
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player viewer)) return;
        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) debounceRefresh(viewer, top);
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player viewer = event.getPlayer();
        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) debounceRefresh(viewer, top);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellManager.SellHolder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Inventory top = event.getInventory();
        int topSize = top.getSize();

        boolean hasTopSlots = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!hasTopSlots) return;

        var sellSlots = manager.definition().items().get("sell-slots").slots();
        var confirmItem = manager.definition().items().get("confirm");
        var confirmSlots = confirmItem != null ? confirmItem.slots() : List.<Integer>of();

        boolean hasInvalidTarget = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .anyMatch(slot -> !isSlotInList(slot, sellSlots) && !isSlotInList(slot, confirmSlots));

        if (hasInvalidTarget) event.setCancelled(true);
        debounceRefresh(viewer, top);
    }

    public void returnItemsToPlayer(Player player, Inventory inv, List<Integer> sellSlots) {
        for (int slot : sellSlots) {
            if (slot >= inv.getSize()) continue;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            inv.setItem(slot, null);
        }
    }

    private boolean isSlotInList(int slot, List<Integer> list) {
        for (int s : list) if (s == slot) return true;
        return false;
    }
}