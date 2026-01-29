package com.ftxeven.aircore.module.gui.invsee;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.player.PlayerInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class TargetListener implements Listener {
    private final AirCore plugin;
    private final InvseeManager invseeManager;
    private final Map<UUID, List<Player>> viewers = new HashMap<>();

    public TargetListener(AirCore plugin, InvseeManager invseeManager) {
        this.plugin = plugin;
        this.invseeManager = invseeManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

        PlayerInventories.InventoryBundle bundle = new PlayerInventories.InventoryBundle(
                target.getInventory().getContents(),
                target.getInventory().getArmorContents(),
                target.getInventory().getItemInOffHand(),
                target.getEnderChest().getContents()
        );

        for (Player viewer : list) {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof InvseeManager.InvseeHolder holder) {
                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, null);
                }
                InvseeSlotMapper.fill(inv, invseeManager.definition(), bundle);
                InvseeSlotMapper.fillCustom(
                        inv,
                        invseeManager.definition(),
                        viewer,
                        Map.of("player", viewer.getName(), "target", holder.targetName()),
                        invseeManager
                );
            }
        }
    }

    public void refreshViewers(UUID targetUUID, PlayerInventories.InventoryBundle bundle) {
        List<Player> list = viewers.get(targetUUID);
        if (list == null) return;

        for (Player viewer : list) {
            Inventory inv = viewer.getOpenInventory().getTopInventory();
            if (inv.getHolder() instanceof InvseeManager.InvseeHolder holder) {
                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, null);
                }
                InvseeSlotMapper.fill(inv, invseeManager.definition(), bundle);
                InvseeSlotMapper.fillCustom(
                        inv,
                        invseeManager.definition(),
                        viewer,
                        Map.of("player", viewer.getName(), "target", holder.targetName()),
                        invseeManager
                );
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof InvseeManager.InvseeHolder holder) {
            Player viewer = (Player) e.getPlayer();
            unregisterViewer(holder.targetUUID(), viewer);
        }
    }

    @EventHandler public void onTargetClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player target) {
            plugin.scheduler().runEntityTask(target, () -> refreshViewers(target));
        }
    }
    @EventHandler public void onTargetDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player target) {
            plugin.scheduler().runEntityTask(target, () -> refreshViewers(target));
        }
    }
    @EventHandler public void onTargetDrop(PlayerDropItemEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player target) {
            plugin.scheduler().runEntityTask(target, () -> refreshViewers(target));
        }
    }
    @EventHandler public void onTargetItemConsume(PlayerItemConsumeEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetItemBreak(PlayerItemBreakEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetItemDamage(PlayerItemDamageEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetSwapHand(PlayerSwapHandItemsEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetBlockPlace(BlockPlaceEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler public void onTargetBlockBreak(BlockBreakEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player player)) return;
        plugin.scheduler().runEntityTask(player, () -> refreshViewers(player));
    }
    @EventHandler
    public void onShootBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        plugin.scheduler().runEntityTask(player, () -> refreshViewers(player));
    }
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player player)) return;
        plugin.scheduler().runEntityTask(player, () -> refreshViewers(player));
    }
    @EventHandler
    public void onRightClickArmor(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        if (slot.isArmor()) {
            plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        plugin.scheduler().runEntityTask(e.getEntity(), () -> refreshViewers(e.getEntity()));
    }
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                () -> refreshViewers(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onBottleInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        if (e.getClickedBlock().getType() == Material.WATER_CAULDRON && item.getType() == Material.GLASS_BOTTLE) {
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                    () -> refreshViewers(e.getPlayer()), 1L);
        }

        if (e.getClickedBlock().getType() == Material.CAULDRON && item.getType() == Material.POTION) {
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                    () -> refreshViewers(e.getPlayer()), 1L);
        }

        if (e.getClickedBlock().getType() == Material.WATER && item.getType() == Material.GLASS_BOTTLE) {
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                    () -> refreshViewers(e.getPlayer()), 1L);
        }
    }

    @EventHandler
    public void onBottleFill(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GLASS_BOTTLE) return;

        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                () -> refreshViewers(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                () -> refreshViewers(e.getPlayer()), 1L);
    }

    @EventHandler public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }

    @EventHandler
    public void onBucketPlace(BlockPlaceEvent e) {
        plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                () -> refreshViewers(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onBucketEntity(PlayerInteractEntityEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());
        if (item.getType() == Material.BUCKET) {
            plugin.scheduler().runEntityTaskDelayed(e.getPlayer(),
                    () -> refreshViewers(e.getPlayer()), 1L);
        }
    }

    @EventHandler public void onUseSpawnEgg(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType().toString().endsWith("_SPAWN_EGG")) {
            plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
        }
    }

    @EventHandler public void onUseSpawnEggOnEntity(PlayerInteractEntityEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());
        if (item.getType().toString().endsWith("_SPAWN_EGG")) {
            plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        plugin.scheduler().runEntityTask(e.getPlayer(), () -> refreshViewers(e.getPlayer()));
    }
}