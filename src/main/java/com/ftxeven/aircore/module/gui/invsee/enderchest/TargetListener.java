package com.ftxeven.aircore.module.gui.invsee.enderchest;

import com.ftxeven.aircore.AirCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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

    private void refreshViewers(Player target) {
        List<Player> list = viewers.get(target.getUniqueId());
        if (list == null) return;

        ItemStack[] contents = target.getEnderChest().getContents();

        for (Player viewer : list) {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, null);
                }
                EnderchestSlotMapper.fill(inv, manager.definition(), contents);
                EnderchestSlotMapper.fillCustom(
                        inv,
                        manager.definition(),
                        viewer,
                        Map.of("player", viewer.getName(), "target", holder.targetName()),
                        manager
                );
            }
        }
    }

    public void refreshViewers(UUID targetUUID, ItemStack[] contents) {
        List<Player> list = viewers.get(targetUUID);
        if (list == null) return;

        for (Player viewer : list) {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, null);
                }
                EnderchestSlotMapper.fill(inv, manager.definition(), contents);
                EnderchestSlotMapper.fillCustom(
                        inv,
                        manager.definition(),
                        viewer,
                        Map.of("player", viewer.getName(), "target", holder.targetName()),
                        manager
                );
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof EnderchestManager.EnderchestHolder holder) {
            Player viewer = (Player) e.getPlayer();
            unregisterViewer(holder.targetUUID(), viewer);
        }
    }

    @EventHandler
    public void onTargetClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player target) {
            plugin.scheduler().runEntityTask(target, () -> refreshViewers(target));
        }
    }

    @EventHandler
    public void onTargetDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player target) {
            plugin.scheduler().runEntityTask(target, () -> refreshViewers(target));
        }
    }

    @EventHandler
    public void onEnderchestOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        if (e.getClickedBlock().getType().name().contains("ENDER")) {
            plugin.scheduler().runEntityTaskDelayed(
                    e.getPlayer(),
                    () -> refreshViewers(e.getPlayer()),
                    1L
            );
        }
    }
}