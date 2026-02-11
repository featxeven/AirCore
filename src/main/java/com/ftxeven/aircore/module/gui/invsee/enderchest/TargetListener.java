package com.ftxeven.aircore.module.gui.invsee.enderchest;

import com.ftxeven.aircore.AirCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class TargetListener implements Listener {
    private final AirCore plugin;
    private final EnderchestManager manager;
    private final Map<UUID, List<Player>> viewers = new HashMap<>();
    private final Set<UUID> pendingRefreshes = new HashSet<>();

    public TargetListener(AirCore plugin, EnderchestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void registerViewer(UUID target, Player viewer) {
        viewers.computeIfAbsent(target, k -> new ArrayList<>()).add(viewer);
    }

    public void unregisterViewer(UUID target, Player viewer) {
        List<Player> list = viewers.get(target);
        if (list != null) {
            list.remove(viewer);
            if (list.isEmpty()) viewers.remove(target);
        }
    }

    private void scheduleRefresh(Player target) {
        if (target == null || !viewers.containsKey(target.getUniqueId())) return;
        if (!pendingRefreshes.add(target.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(target, () -> {
            try {
                refreshViewers(target.getUniqueId(), target.getEnderChest().getContents());
            } finally {
                pendingRefreshes.remove(target.getUniqueId());
            }
        }, 1L);
    }

    public void refreshViewers(UUID targetUUID, ItemStack[] contents) {
        List<Player> list = viewers.get(targetUUID);
        if (list == null || list.isEmpty()) return;

        for (Player viewer : list) {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof EnderchestManager.EnderchestHolder) {
                EnderchestSlotMapper.fill(inv, manager.definition(), contents);
                if (inv.getSize() != 27) {
                    manager.refreshFillers(inv, viewer);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
            if (e.getInventory().getSize() == 27) {
                manager.applyEnderchestToTarget(holder.targetUUID(), e.getInventory().getContents());
            }
            unregisterViewer(holder.targetUUID(), (Player) e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();

        if (top.getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
            plugin.scheduler().runEntityTaskDelayed(player, () -> {
                ItemStack[] contents = EnderchestSlotMapper.extractContents(top, manager.definition());
                manager.applyEnderchestToTarget(holder.targetUUID(), contents);
                refreshViewers(holder.targetUUID(), contents);
            }, 1L);
        } else if (top.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetDrag(InventoryDragEvent e) {
        scheduleRefresh((Player) e.getWhoClicked());
    }

    @EventHandler
    public void onEnderchestOpen(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (e.getClickedBlock().getType().name().contains("ENDER_CHEST")) {
                scheduleRefresh(e.getPlayer());
            }
        }
    }
}