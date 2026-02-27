package com.ftxeven.aircore.core.modules.gui.invsee.enderchest;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TargetListener implements Listener {
    private final AirCore plugin;
    private final EnderchestManager manager;
    private final Map<UUID, List<Player>> viewers = new ConcurrentHashMap<>();
    private final Set<UUID> joinLock = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();

    public TargetListener(AirCore plugin, EnderchestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void lock(UUID uuid) { joinLock.add(uuid); }
    public void unlock(UUID uuid) { joinLock.remove(uuid); }

    public void registerViewer(UUID target, Player viewer) {
        viewers.computeIfAbsent(target, k -> new CopyOnWriteArrayList<>()).add(viewer);
    }

    public void unregisterViewer(UUID target, Player viewer) {
        viewers.computeIfPresent(target, (k, list) -> {
            list.remove(viewer);
            return list.isEmpty() ? null : list;
        });
    }

    private void scheduleRefresh(Player target) {
        if (target == null || joinLock.contains(target.getUniqueId())) return;
        if (!viewers.containsKey(target.getUniqueId())) return;
        if (!pendingRefreshes.add(target.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(target, () -> {
            try {
                if (target.isOnline()) {
                    refreshViewers(target.getUniqueId(), target.getEnderChest().getContents());
                }
            } finally {
                pendingRefreshes.remove(target.getUniqueId());
            }
        }, 1L);
    }

    public void refreshViewers(UUID targetUUID, ItemStack[] contents) {
        List<Player> list = viewers.get(targetUUID);
        if (list == null) return;

        list.forEach(viewer -> plugin.scheduler().runEntityTask(viewer, () -> {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
                if (holder.isOwn()) {
                    inv.setContents(contents);
                } else {
                    EnderchestSlotMapper.fill(inv, manager.definition(), contents);
                }
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
            if (holder.isOwn()) {
                manager.applyEnderchestToTarget(holder.targetUUID(), e.getInventory().getContents());
            }
            unregisterViewer(holder.targetUUID(), (Player) e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();

        if (top.getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
            plugin.scheduler().runEntityTaskDelayed(e.getWhoClicked(), () -> {
                ItemStack[] contents = holder.isOwn()
                        ? top.getContents()
                        : EnderchestSlotMapper.extractContents(top, manager.definition());

                manager.applyEnderchestToTarget(holder.targetUUID(), contents);
                refreshViewers(holder.targetUUID(), contents);
            }, 1L);
        } else if (top.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            scheduleRefresh((Player) e.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        scheduleRefresh((Player) e.getWhoClicked());
    }

    @EventHandler
    public void onEnderchestOpen(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().name().contains("ENDER_CHEST")) {
            scheduleRefresh(e.getPlayer());
        }
    }
}