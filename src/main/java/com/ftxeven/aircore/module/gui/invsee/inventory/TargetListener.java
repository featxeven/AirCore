package com.ftxeven.aircore.module.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.player.PlayerInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class TargetListener implements Listener {
    private final AirCore plugin;
    private final InventoryManager inventoryManager;
    private final Map<UUID, List<Player>> viewers = new HashMap<>();
    private final Set<UUID> pendingRefreshes = new HashSet<>();

    public TargetListener(AirCore plugin, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
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

        plugin.scheduler().runEntityTask(target, () -> {
            try {
                refreshViewers(target.getUniqueId(), createBundle(target));
            } finally {
                pendingRefreshes.remove(target.getUniqueId());
            }
        });
    }

    private PlayerInventories.InventoryBundle createBundle(Player target) {
        return new PlayerInventories.InventoryBundle(
                target.getInventory().getContents(),
                target.getInventory().getArmorContents(),
                target.getInventory().getItemInOffHand(),
                target.getEnderChest().getContents()
        );
    }

    public void refreshViewers(UUID targetUUID, PlayerInventories.InventoryBundle bundle) {
        List<Player> list = viewers.get(targetUUID);
        if (list == null || list.isEmpty()) return;

        for (Player viewer : list) {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof InventoryManager.InvseeHolder holder) {
                // Clear and refill - InventorySlotMapper handles the logic
                top.clear();
                InventorySlotMapper.fill(top, inventoryManager.definition(), bundle);
                InventorySlotMapper.fillCustom(
                        top,
                        inventoryManager.definition(),
                        viewer,
                        Map.of("player", viewer.getName(), "target", holder.targetName()),
                        inventoryManager
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof InventoryManager.InvseeHolder holder) {
            unregisterViewer(holder.targetUUID(), (Player) e.getPlayer());
        }
    }

    // --- Grouped Generic Interaction Handlers ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryChange(InventoryClickEvent e) { scheduleRefresh((Player) e.getWhoClicked()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) { scheduleRefresh((Player) e.getWhoClicked()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(PlayerDropItemEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p) scheduleRefresh(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(PlayerItemConsumeEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(PlayerSwapHandItemsEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(BlockPlaceEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(BlockBreakEvent e) { scheduleRefresh(e.getPlayer()); }

    // --- Combat & Consumables ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(ProjectileLaunchEvent e) { if (e.getEntity().getShooter() instanceof Player p) scheduleRefresh(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(EntityShootBowEvent e) { if (e.getEntity() instanceof Player p) scheduleRefresh(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(PlayerItemDamageEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { scheduleRefresh(e.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(), () -> scheduleRefresh(e.getPlayer()), 1L);
    }

    // --- Specialized Interaction Handlers ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;

        Material mat = item.getType();
        // Check for Armor Right-Click, Buckets, or Spawn Eggs
        if (mat.getEquipmentSlot().isArmor() || mat == Material.BUCKET || mat == Material.GLASS_BOTTLE || mat.name().endsWith("_SPAWN_EGG")) {
            // Delay by 1 tick because the item hasn't actually left/entered the inventory yet in some cases
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(), () -> scheduleRefresh(e.getPlayer()), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(), () -> scheduleRefresh(e.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());
        if (item.getType() == Material.BUCKET || item.getType().name().endsWith("_SPAWN_EGG")) {
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(), () -> scheduleRefresh(e.getPlayer()), 1L);
        }
    }
}