package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.dao.PlayerInventories;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TargetListener implements Listener {
    private final AirCore plugin;
    private final InventoryManager inventoryManager;
    private final Map<UUID, List<Player>> viewers = new ConcurrentHashMap<>();
    private final Set<UUID> joinLock = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> pendingRefreshes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TargetListener(AirCore plugin, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
    }

    public boolean isBeingWatched(UUID targetUUID) { return viewers.containsKey(targetUUID); }
    public void lock(UUID uuid) { joinLock.add(uuid); }
    public void unlock(UUID uuid) { joinLock.remove(uuid); }
    public List<Player> getViewers(UUID targetUUID) { return viewers.getOrDefault(targetUUID, List.of()); }

    public void registerViewer(UUID target, Player viewer) {
        viewers.computeIfAbsent(target, k -> new CopyOnWriteArrayList<>()).add(viewer);
    }

    public void unregisterViewer(UUID target, Player viewer) {
        List<Player> list = viewers.get(target);
        if (list != null) {
            list.remove(viewer);
            if (list.isEmpty()) viewers.remove(target);
        }
    }

    private void scheduleRefresh(Player target) {
        if (target == null || joinLock.contains(target.getUniqueId())) return;

        UUID targetUUID = target.getUniqueId();
        List<Player> viewerList = viewers.get(targetUUID);

        if (viewerList == null || viewerList.isEmpty()) return;
        if (!pendingRefreshes.add(targetUUID)) return;

        plugin.scheduler().runEntityTaskDelayed(target, () -> {
            try {
                if (target.isOnline()) {
                    PlayerInventories.InventoryBundle bundle = createBundle(target);

                    for (Player viewer : viewerList) {
                        Map<String, String> placeholders = Map.of(
                                "player", viewer.getName(),
                                "target", target.getName()
                        );
                        refreshViewers(targetUUID, bundle, viewer, placeholders);
                    }
                }
            } finally {
                pendingRefreshes.remove(targetUUID);
            }
        }, 1L);
    }

    private PlayerInventories.InventoryBundle createBundle(Player target) {
        return new PlayerInventories.InventoryBundle(
                target.getInventory().getContents(),
                target.getInventory().getArmorContents(),
                target.getInventory().getItemInOffHand(),
                target.getEnderChest().getContents()
        );
    }

    public void refreshViewers(UUID targetUUID, PlayerInventories.InventoryBundle bundle, Player viewer, Map<String, String> ph) {
        plugin.scheduler().runEntityTask(viewer, () -> {
            Inventory top = viewer.getOpenInventory().getTopInventory();

            if (top.getHolder() instanceof InventoryManager.InvseeHolder holder) {
                if (!holder.targetUUID().equals(targetUUID)) return;

                InventorySlotMapper.fill(top, inventoryManager.definition(), bundle, viewer, ph);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof InventoryManager.InvseeHolder holder) {
            unregisterViewer(holder.targetUUID(), (Player) e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction().name().contains("RIGHT_CLICK") && e.hasItem()) {
            scheduleRefresh(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent e) {
        scheduleRefresh(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent e) {
        scheduleRefresh(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) scheduleRefresh(p);
    }

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
    public void onCommand(PlayerCommandPreprocessEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(BlockPlaceEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericAction(BlockBreakEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(ProjectileLaunchEvent e) { if (e.getEntity().getShooter() instanceof Player p) scheduleRefresh(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(EntityShootBowEvent e) { if (e.getEntity() instanceof Player p) scheduleRefresh(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatAction(PlayerItemDamageEvent e) { scheduleRefresh(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { scheduleRefresh(e.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) { scheduleRefresh(e.getPlayer()); }
}