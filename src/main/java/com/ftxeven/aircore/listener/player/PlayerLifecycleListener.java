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
import org.bukkit.WeatherType;
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        event.joinMessage(null);

        setInvLock(uuid, true);

        plugin.scheduler().runAsync(() -> {
            if (plugin.database() == null || plugin.database().isClosed()) return;

            try {
                JoinData data = loadPlayerDataAsync(player);

                plugin.scheduler().runEntityTask(player, () -> {
                    try {
                        if (!player.isOnline()) return;
                        applyPlayerDataSync(player, data);
                    } finally {
                        setInvLock(uuid, false);
                    }

                    postJoinActions(player, data.hasJoinedBefore(), data.joinIndex());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load player data for " + player.getName() + ": " + e.getMessage());
                plugin.scheduler().runEntityTask(player, () -> setInvLock(uuid, false));
            }
        });
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

        savePlayerDataAsync(player);
        cleanupPlayerData(player);
        handleBroadcastOnQuit(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.utility().back().setLastDeath(player.getUniqueId(), player.getLocation());

        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        if (plugin.scheduler().isFoliaServer()) {
            justDied.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.scheduler().isFoliaServer() && plugin.config().teleportToSpawnOnDeath()) {
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (spawn != null) event.setRespawnLocation(spawn);
        }
        handlePostRespawn(event.getPlayer());
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

    private JoinData loadPlayerDataAsync(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasJoinedBefore = plugin.database().records().hasJoinedBefore(uuid);

        plugin.core().commandCooldowns().load(uuid);
        plugin.database().records().updateJoinInfo(player);

        int joinIndex = hasJoinedBefore
                ? Optional.ofNullable(plugin.database().records().getJoinIndex(uuid)).orElse(0)
                : plugin.database().records().createPlayerRecord(uuid, player.getName());

        Map<ToggleService.Toggle, Boolean> toggles = new EnumMap<>(ToggleService.Toggle.class);
        for (var t : ToggleService.Toggle.values()) {
            toggles.put(t, plugin.database().records().getToggle(uuid, t));
        }

        return new JoinData(
                hasJoinedBefore,
                joinIndex,
                toggles,
                plugin.database().records().getWalkSpeed(uuid),
                plugin.database().records().getFlySpeed(uuid),
                plugin.database().records().getBalance(uuid),
                plugin.database().records().getPlayerTime(uuid),
                plugin.database().records().getPlayerWeather(uuid),
                plugin.database().inventories().loadAllInventory(uuid)
        );
    }

    private void applyPlayerDataSync(Player player, JoinData data) {
        UUID uuid = player.getUniqueId();

        plugin.core().toggles().load(uuid, data.toggles());
        plugin.economy().balances().setBalanceLocal(uuid, data.balance());

        plugin.home().homes().loadFromDatabase(uuid, plugin.database().homes().load(uuid));
        plugin.core().blocks().loadExistingBlocks(uuid, plugin.database().blocks().load(uuid));

        applyAttributes(player, data.walkSpeed(), data.flySpeed(), data.toggles().get(ToggleService.Toggle.FLY));
        applyInventoryBundle(player, data.invBundle());

        if (data.playerTime() != -1L) {
            player.setPlayerTime(data.playerTime(), false);
        }
        if (data.playerWeather() != null) {
            try { player.setPlayerWeather(WeatherType.valueOf(data.playerWeather())); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private void postJoinActions(Player player, boolean hasJoinedBefore, int joinIndex) {
        if ((!hasJoinedBefore && plugin.config().teleportToSpawnOnFirstJoin()) || plugin.config().teleportToSpawnOnJoin()) {
            teleportToSpawn(player);
        }
        handleMotdAndBroadcastOnJoin(player, hasJoinedBefore, joinIndex);
        handleUpdateNotification(player);
    }

    private void handleMotdAndBroadcastOnJoin(Player p, boolean joinedBefore, int idx) {
        if (!joinedBefore) plugin.kit().kits().grantFirstJoinKit(p);

        var placeholders = Map.of("player", p.getName(), "unique", String.valueOf(idx));
        String group = fetchVaultGroup(p);

        if (!joinedBefore) {
            MessageUtil.sendRaw(p, plugin.config().motdFirstJoin(), placeholders);
            broadcastToAll(plugin.config().broadcastFirstJoin(), placeholders);
        } else {
            MessageUtil.sendRaw(p, plugin.config().motdJoin(group), placeholders);
            broadcastToAll(plugin.config().broadcastJoinFormat(group), placeholders);
        }
    }

    private void savePlayerDataAsync(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
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

    private void handlePostRespawn(Player p) {
        boolean fly = plugin.core().toggles().isEnabled(p.getUniqueId(), ToggleService.Toggle.FLY);
        plugin.scheduler().runEntityTask(p, () -> {
            if (p.isOnline()) applyAttributes(p, p.getWalkSpeed() * 5.0, p.getFlySpeed() * 10.0, fly);
        });
    }

    private void handleBroadcastOnQuit(Player p) {
        String group = fetchVaultGroup(p);
        broadcastToAll(plugin.config().broadcastLeaveFormat(group), Map.of("player", p.getName()));
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

    private void teleportToSpawn(Player player) {
        plugin.scheduler().runDelayed(() -> {
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (player.isOnline() && spawn != null) plugin.core().teleports().teleport(player, spawn);
        }, 1L);
    }

    private void broadcastToAll(Object messageObj, Map<String, String> placeholders) {
        if (messageObj == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendRaw(online, messageObj, placeholders);
        }
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

    private String fetchVaultGroup(Player p) {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp != null) {
                try {
                    return rsp.getProvider().getPrimaryGroup(p);
                } catch (UnsupportedOperationException ignored) { }
            }
        }
        return "default";
    }

    private record JoinData(
            boolean hasJoinedBefore,
            int joinIndex,
            Map<ToggleService.Toggle, Boolean> toggles,
            double walkSpeed,
            double flySpeed,
            double balance,
            long playerTime,
            String playerWeather,
            PlayerInventories.InventoryBundle invBundle
    ) {}
}