package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.module.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SellListener implements Listener {

    private final AirCore plugin;
    private final SellManager manager;
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();

    public SellListener(AirCore plugin, SellManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void debounceRefresh(Player viewer, Inventory inv) {
        if (!pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                if (viewer.getOpenInventory().getTopInventory().equals(inv) &&
                        inv.getHolder() instanceof SellManager.SellHolder) {
                    manager.refreshConfirmButton(inv, viewer);
                }
            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) {
            debounceRefresh((Player) event.getWhoClicked(), top);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellManager.SellHolder)) return;

        Player player = (Player) e.getPlayer();
        if (manager.isTransitioning(player.getUniqueId())) return;

        Inventory inv = e.getInventory();
        var sellSlots = manager.definition().items().get("sell-slots").slots();

        if (plugin.gui().isReloading() || manager.consumeProcessedSale(player.getUniqueId())) {
            returnItemsToPlayer(player, inv, sellSlots);
            return;
        }

        boolean sellOnClose = manager.definition().config().getBoolean("sell-on-inventory-close", false);
        if (sellOnClose) {
            SellSlotMapper.WorthResult result = SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), manager.definition());

            if (result.hasUnsupported() || result.total() <= 0) {
                if (result.total() <= 0 && hasItems(inv, sellSlots)) {
                    MessageUtil.send(player, "economy.sell.error-invalid", Map.of());
                } else if (result.hasUnsupported()) {
                    MessageUtil.send(player, "economy.sell.error-failed", Map.of());
                }
                returnItemsToPlayer(player, inv, sellSlots);
                return;
            }

            double rounded = plugin.economy().formats().round(result.total());
            if (plugin.economy().transactions().deposit(player.getUniqueId(), rounded).type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(player, "economy.sell.success", Map.of("amount", plugin.economy().formats().formatAmount(rounded)));

                if (manager.definition().config().getBoolean("sell-logs-on-console", false)) {
                    plugin.getLogger().info(player.getName() + " sold items on close for $" + rounded);
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
        if (event.getEntity() instanceof Player viewer) handleExternalUpdate(viewer);
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        handleExternalUpdate(event.getPlayer());
    }

    private void handleExternalUpdate(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof SellManager.SellHolder) debounceRefresh(player, top);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof SellManager.SellHolder)) return;

        int topSize = top.getSize();
        var sellSlots = manager.definition().items().get("sell-slots").slots();

        boolean invalid = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .anyMatch(slot -> !sellSlots.contains(slot));

        if (invalid) {
            event.setCancelled(true);
        } else {
            debounceRefresh((Player) event.getWhoClicked(), top);
        }
    }

    public void returnItemsToPlayer(Player player, Inventory inv, List<Integer> sellSlots) {
        for (int slot : sellSlots) {
            if (slot >= inv.getSize()) continue;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
            inv.setItem(slot, null);
        }
    }

    private boolean hasItems(Inventory inv, List<Integer> slots) {
        for (int s : slots) {
            ItemStack item = inv.getItem(s);
            if (item != null && !item.getType().isAir()) return true;
        }
        return false;
    }
}