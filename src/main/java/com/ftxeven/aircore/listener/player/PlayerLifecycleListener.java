package com.ftxeven.aircore.listener.player;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.invsee.enderchest.EnderchestManager;
import com.ftxeven.aircore.core.gui.invsee.inventory.InventoryManager;
import com.ftxeven.aircore.core.gui.invsee.inventory.InventorySlotMapper;
import com.ftxeven.aircore.database.dao.PlayerInventories;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.BossbarUtil;
import com.ftxeven.aircore.util.MessageUtil;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLifecycleListener implements Listener {

    private final AirCore plugin;
    private final Set<UUID> justDied = Collections.synchronizedSet(new HashSet<>());
    private static final long AUTOSAVE_INTERVAL_TICKS = 2400L;

    public PlayerLifecycleListener(AirCore plugin) {
        this.plugin = plugin;
        startInventoryAutoSave();
    }

    private void startInventoryAutoSave() {
        plugin.scheduler().runAsyncTimer(() -> {
            var players = Bukkit.getOnlinePlayers();
            if (players.isEmpty()) return;

            Map<UUID, PlayerInventories.InventorySnapshot> snapshots = new ConcurrentHashMap<>();
            players.forEach(p -> plugin.scheduler().runEntityTask(p, () -> {
                snapshots.put(p.getUniqueId(), plugin.database().inventories().createSnapshot(p));
                if (snapshots.size() == players.size()) {
                    plugin.scheduler().runAsync(() -> plugin.database().inventories().saveAllSync(snapshots));
                }
            }));
        }, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        event.joinMessage(null);

        setInvLock(uuid, true);

        plugin.scheduler().runAsync(() -> {
            if (plugin.database() == null || plugin.database().isClosed()) {
                return;
            }

            try {
                boolean hasJoinedBefore = plugin.database().records().hasJoinedBefore(uuid);
                plugin.core().commandCooldowns().load(uuid);

                int joinIndex = hasJoinedBefore ?
                        Optional.ofNullable(plugin.database().records().getJoinIndex(uuid)).orElse(0) :
                        plugin.database().records().createPlayerRecord(uuid, player.getName());

                if (hasJoinedBefore) {
                    plugin.database().records().updateJoinInfo(player);
                } else {
                    plugin.database().records().updateJoinInfo(player);
                }

                Map<ToggleService.Toggle, Boolean> toggles = new EnumMap<>(ToggleService.Toggle.class);
                for (var t : ToggleService.Toggle.values()) {
                    toggles.put(t, plugin.database().records().getToggle(uuid, t));
                }

                double walk = plugin.database().records().getWalkSpeed(uuid);
                double fly = plugin.database().records().getFlySpeed(uuid);
                double bal = plugin.database().records().getBalance(uuid);
                var invBundle = plugin.database().inventories().loadAllInventory(uuid);

                plugin.scheduler().runEntityTask(player, () -> {
                    try {
                        if (!player.isOnline()) return;

                        plugin.core().toggles().load(uuid, toggles);
                        plugin.economy().balances().setBalanceLocal(uuid, bal);
                        plugin.home().homes().loadFromDatabase(uuid, plugin.database().homes().load(uuid));

                        var blockedData = plugin.database().blocks().load(uuid);
                        plugin.core().blocks().loadExistingBlocks(uuid, blockedData);

                        applyAttributes(player, walk, fly, toggles.get(ToggleService.Toggle.FLY));
                        applyInventoryBundle(player, invBundle);
                    } finally {
                        setInvLock(uuid, false);
                    }

                    if ((!hasJoinedBefore && plugin.config().teleportToSpawnOnFirstJoin()) || plugin.config().teleportToSpawnOnJoin()) {
                        teleportToSpawn(player);
                    }

                    handleMotdAndBroadcastOnJoin(player, hasJoinedBefore, joinIndex);
                    handleUpdateNotification(player);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load player data for " + player.getName() + ": " + e.getMessage());
                plugin.scheduler().runEntityTask(player, () -> setInvLock(uuid, false));
            }
        });
    }

    private void applyAttributes(Player p, double walk, double fly, boolean flyToggle) {
        p.setWalkSpeed((float) Math.min(Math.max(walk * 0.2, 0.0), 1.0));
        p.setFlySpeed((float) Math.min(Math.max(fly * 0.1, 0.0), 1.0));

        boolean canFly = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || flyToggle;
        p.setAllowFlight(canFly);
        if (!canFly) p.setFlying(false);
    }

    private void applyInventoryBundle(Player p, PlayerInventories.InventoryBundle bundle) {
        if (bundle == null) return;

        InventoryManager im = plugin.gui().getInventoryManager();
        UUID uuid = p.getUniqueId();

        ItemStack[] contents = bundle.contents(), armor = bundle.armor(), ec = bundle.enderChest();
        ItemStack offhand = bundle.offhand();

        if (im != null && im.getTargetListener().isBeingWatched(uuid)) {
            var viewers = im.getTargetListener().getViewers(uuid);
            if (!viewers.isEmpty()) {
                var gui = InventorySlotMapper.extractBundle(viewers.getFirst().getOpenInventory().getTopInventory(), im.definition());
                contents = gui.contents(); armor = gui.armor(); offhand = gui.offhand();
            }
        }

        p.getInventory().setContents(contents);
        p.getInventory().setArmorContents(armor);
        p.getInventory().setItemInOffHand(offhand);
        p.getEnderChest().setContents(ec);
        p.updateInventory();
    }

    private void setInvLock(UUID uuid, boolean lock) {
        InventoryManager im = plugin.gui().getInventoryManager();
        EnderchestManager em = plugin.gui().getEnderchestManager();
        if (lock) {
            if (im != null) im.getTargetListener().lock(uuid);
            if (em != null) em.getTargetListener().lock(uuid);
        } else {
            if (im != null) im.getTargetListener().unlock(uuid);
            if (em != null) em.getTargetListener().unlock(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        event.quitMessage(null);
        justDied.remove(uuid);

        BossbarUtil.hideAll(player);

        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        var loc = player.getLocation();
        var snap = plugin.database().inventories().createSnapshot(player);

        Runnable saveTask = () -> {
            if (plugin.database() == null || plugin.database().isClosed()) return;

            try {
                plugin.database().records().setLocation(uuid, loc);
                plugin.database().inventories().saveAllSync(Map.of(uuid, snap));
                plugin.economy().balances().unloadBalance(uuid);
                plugin.home().homes().unload(uuid);
            } catch (Exception e) {
                if (plugin.isEnabled()) {
                    plugin.getLogger().warning("Failed to save data on quit for " + player.getName() + ": " + e.getMessage());
                }
            }
        };

        if (!plugin.isEnabled()) {
            saveTask.run();
        } else {
            plugin.scheduler().runAsync(saveTask);
        }

        cleanupPlayerData(player);
        handleBroadcastOnQuit(player);
    }

    private void cleanupPlayerData(Player p) {
        UUID uuid = p.getUniqueId();
        if (!plugin.config().retainRequestStateOnLogout()) {
            plugin.teleport().requests().clearRequests(uuid);
            plugin.teleport().cooldowns().clear(uuid);
        }
        plugin.core().commandCooldowns().clear(uuid);
        plugin.utility().afk().clearAfk(uuid);
        plugin.core().blocks().unload(uuid);
    }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent e) {
        plugin.utility().back().setLastDeath(e.getEntity().getUniqueId(), e.getEntity().getLocation());
        if (plugin.core().teleports().hasCountdown(e.getEntity())) plugin.core().teleports().cancelCountdown(e.getEntity(), false);
        if (plugin.scheduler().isFoliaServer()) justDied.add(e.getEntity().getUniqueId());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.scheduler().isFoliaServer() && plugin.config().teleportToSpawnOnDeath()) {
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (spawn != null) e.setRespawnLocation(spawn);
        }
        handlePostRespawn(e.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.scheduler().isFoliaServer() || !(event.getPlayer() instanceof Player p)) return;
        if (!justDied.remove(p.getUniqueId())) return;

        if (plugin.config().teleportToSpawnOnDeath()) {
            Location spawn = plugin.utility().spawn().loadSpawn();
            plugin.scheduler().runEntityTaskDelayed(p, () -> {
                if (p.isOnline() && spawn != null) plugin.core().teleports().teleport(p, spawn);
                handlePostRespawn(p);
            }, 1L);
        } else {
            handlePostRespawn(p);
        }
    }

    private void handlePostRespawn(Player p) {
        boolean fly = plugin.core().toggles().isEnabled(p.getUniqueId(), ToggleService.Toggle.FLY);
        plugin.scheduler().runEntityTask(p, () -> {
            if (p.isOnline()) applyAttributes(p, p.getWalkSpeed() * 5.0, p.getFlySpeed() * 10.0, fly);
        });
    }

    private void handleUpdateNotification(Player player) {
        if (!plugin.config().notifyUpdates()) return;
        String latest = plugin.getLatestVersion();
        if (latest != null && (player.hasPermission("aircore.command.admin") || player.isOp())) {
            plugin.scheduler().runDelayed(() -> {
                if (player.isOnline()) MessageUtil.send(player, "general.plugin-outdated",
                        Map.of("current", plugin.getPluginMeta().getVersion(), "latest", latest));
            }, 20L);
        }
    }

    private void teleportToSpawn(Player player) {
        plugin.scheduler().runDelayed(() -> {
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (player.isOnline() && spawn != null) plugin.core().teleports().teleport(player, spawn);
        }, 1L);
    }

    private void handleMotdAndBroadcastOnJoin(Player p, boolean joinedBefore, int idx) {
        if (!joinedBefore) plugin.kit().kits().grantFirstJoinKit(p);
        var ph = Map.of("player", p.getName(), "unique", String.valueOf(idx));

        if (!joinedBefore) {
            MessageUtil.sendRaw(p, plugin.config().motdFirstJoin(), ph);

            Object br = plugin.config().broadcastFirstJoin();
            if (br != null) {
                for (Player online : Bukkit.getOnlinePlayers()) MessageUtil.sendRaw(online, br, ph);
            }
        } else {
            String group = fetchVaultGroup(p);
            MessageUtil.sendRaw(p, plugin.config().motdJoin(group), ph);

            Object br = plugin.config().broadcastJoinFormat(group);
            if (br != null) {
                for (Player online : Bukkit.getOnlinePlayers()) MessageUtil.sendRaw(online, br, ph);
            }
        }
    }

    private void handleBroadcastOnQuit(Player p) {
        String group = fetchVaultGroup(p);
        Object br = plugin.config().broadcastLeaveFormat(group);
        if (br != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                MessageUtil.sendRaw(online, br, Map.of("player", p.getName()));
            }
        }
    }

    private String fetchVaultGroup(Player p) {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp != null) {
                Permission perms = rsp.getProvider();
                try {
                    return perms.getPrimaryGroup(p);
                } catch (UnsupportedOperationException ignored) { }
            }
        }
        return "default";
    }
}