package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.player.PlayerInventories;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlayerLifecycleListener implements Listener {

    private final AirCore plugin;
    private final Set<UUID> justDied = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> dirtyPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final long AUTOSAVE_INTERVAL_TICKS = 2400L;

    public PlayerLifecycleListener(AirCore plugin) {
        this.plugin = plugin;
        startInventoryAutoSave();
    }

    private void startInventoryAutoSave() {
        plugin.scheduler().runAsyncTimer(() -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty()) return;

            Map<UUID, PlayerInventories.InventorySnapshot> snapshots = new ConcurrentHashMap<>();

            for (Player player : players) {
                plugin.scheduler().runEntityTask(player, () -> {
                    snapshots.put(player.getUniqueId(), plugin.database().inventories().createSnapshot(player));

                    if (snapshots.size() == players.size()) {
                        plugin.scheduler().runAsync(() -> plugin.database().inventories().saveAllSync(snapshots));
                    }
                });
            }
        }, AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        event.joinMessage(null);
        String lowerName = player.getName().toLowerCase();
        plugin.getNameCache().put(lowerName, uuid);

        plugin.scheduler().runAsync(() -> {
            boolean hasJoinedBefore = plugin.database().records().hasJoinedBefore(uuid);
            plugin.core().commandCooldowns().load(uuid);

            int joinIndex;
            if (!hasJoinedBefore) {
                joinIndex = plugin.database().records().createPlayerRecord(uuid, player.getName());
            } else {
                Integer idx = plugin.database().records().getJoinIndex(uuid);
                joinIndex = idx != null ? idx : 0;
            }

            boolean chat = plugin.database().records().getToggle(uuid, ToggleService.Toggle.CHAT.getColumn());
            boolean mentions = plugin.database().records().getToggle(uuid, ToggleService.Toggle.MENTIONS.getColumn());
            boolean pm = plugin.database().records().getToggle(uuid, ToggleService.Toggle.PM.getColumn());
            boolean socialspy = plugin.database().records().getToggle(uuid, ToggleService.Toggle.SOCIALSPY.getColumn());
            boolean pay = plugin.database().records().getToggle(uuid, ToggleService.Toggle.PAY.getColumn());
            boolean teleport = plugin.database().records().getToggle(uuid, ToggleService.Toggle.TELEPORT.getColumn());
            boolean god = plugin.database().records().getToggle(uuid, ToggleService.Toggle.GOD.getColumn());
            boolean flyEnabled = plugin.database().records().getToggle(uuid, ToggleService.Toggle.FLY.getColumn());

            double walkVal = plugin.database().records().getWalkSpeed(uuid);
            double flyVal = plugin.database().records().getFlySpeed(uuid);
            double balance = plugin.database().records().getBalance(uuid);

            var block = plugin.database().blocks().load(uuid);
            var homes = plugin.database().homes().load(uuid);
            var bundle = plugin.database().inventories().loadAllInventory(uuid);

            plugin.scheduler().runEntityTask(player, () -> {
                if (!player.isOnline()) return;

                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.CHAT, chat);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.MENTIONS, mentions);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.PM, pm);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.SOCIALSPY, socialspy);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.PAY, pay);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.TELEPORT, teleport);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.GOD, god);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.FLY, flyEnabled);

                player.setWalkSpeed((float) Math.min(Math.max(walkVal * 0.2, 0.0), 1.0));
                player.setFlySpeed((float) Math.min(Math.max(flyVal * 0.1, 0.0), 1.0));

                GameMode mode = player.getGameMode();
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                    player.setAllowFlight(true);
                } else {
                    player.setAllowFlight(flyEnabled);
                    if (!flyEnabled) player.setFlying(false);
                }

                block.forEach(target -> plugin.core().blocks().block(uuid, target));
                plugin.economy().balances().setBalanceLocal(uuid, balance);

                plugin.home().homes().loadFromDatabase(uuid, homes);

                if (bundle != null) {
                    player.getInventory().setContents(bundle.contents());
                    player.getInventory().setArmorContents(bundle.armor());
                    if (bundle.offhand() != null) player.getInventory().setItemInOffHand(bundle.offhand());
                    player.getEnderChest().setContents(bundle.enderChest());
                    player.updateInventory();
                }

                if ((!hasJoinedBefore && plugin.config().teleportToSpawnOnFirstJoin()) || plugin.config().teleportToSpawnOnJoin()) {
                    teleportToSpawn(player);
                }

                handleMotdAndBroadcastOnJoin(player, hasJoinedBefore, joinIndex);
                handleUpdateNotification(player);
            });
        });
    }

    private void handleUpdateNotification(Player player) {
        if (plugin.config().notifyUpdates()) {
            String latest = plugin.getLatestVersion();
            if (latest != null && (player.hasPermission("aircore.command.admin") || player.isOp())) {
                plugin.scheduler().runDelayed(() -> {
                    if (player.isOnline()) {
                        MessageUtil.send(player, "general.plugin-outdated", Map.of(
                                "current", plugin.getPluginMeta().getVersion(),
                                "latest", latest
                        ));
                    }
                }, 20L);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        event.quitMessage(null);
        justDied.remove(uuid);
        dirtyPlayers.remove(uuid);

        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        Location quitLoc = player.getLocation();
        PlayerInventories.InventorySnapshot snapshot = plugin.database().inventories().createSnapshot(player);

        plugin.scheduler().runAsync(() -> {
            try {
                plugin.database().records().setLocation(uuid, quitLoc);

                plugin.database().inventories().saveAllSync(Map.of(uuid, snapshot));

                plugin.economy().balances().unloadBalance(uuid);
                plugin.home().homes().unload(uuid);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save player data on quit for " + player.getName() + ": " + e.getMessage());
            }
        });

        try {
            if (!plugin.config().retainRequestStateOnLogout()) {
                plugin.teleport().requests().clearRequests(uuid);
                plugin.teleport().cooldowns().clear(uuid);
            }
            plugin.core().commandCooldowns().clear(uuid);
            plugin.utility().afk().clearAfk(uuid);
            plugin.core().permissionGroups().clearAttachment(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clean up player data for " + player.getName() + ": " + e.getMessage());
        }

        handleBroadcastOnQuit(player);
    }

    // Paper/Spigot: Uses PlayerRespawnEvent
    // Folia: Uses PlayerDeathEvent + InventoryCloseEvent workaround
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!plugin.scheduler().isFoliaServer() && plugin.config().teleportToSpawnOnDeath()) {
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (spawn != null && spawn.getWorld() != null) {
                event.setRespawnLocation(spawn);
            }
        }

        handlePostRespawn(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        plugin.utility().back().setLastDeath(uuid, player.getLocation());

        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        if (plugin.scheduler().isFoliaServer()) {
            justDied.add(uuid);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.scheduler().isFoliaServer()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (!justDied.contains(uuid)) return;

        justDied.remove(uuid);

        if (plugin.config().teleportToSpawnOnDeath()) {
            Location spawn = plugin.utility().spawn().loadSpawn();

            if (spawn != null && spawn.getWorld() != null) {
                plugin.scheduler().runEntityTaskDelayed(player, () -> {
                    if (player.isOnline()) {
                        plugin.core().teleports().teleport(player, spawn);
                        handlePostRespawn(player);
                    }
                }, 1L);
            } else {
                handlePostRespawn(player);
            }
        } else {
            handlePostRespawn(player);
        }
    }

    private void handlePostRespawn(Player player) {
        UUID uuid = player.getUniqueId();
        GameMode mode = player.getGameMode();
        boolean flyEnabled = plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.FLY);

        plugin.scheduler().runEntityTask(player, () -> {
            if (!player.isOnline()) return;

            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            } else if (flyEnabled) {
                player.setAllowFlight(true);
                player.setFlying(false);
            } else {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        });
    }

    private void teleportToSpawn(Player player) {
        plugin.scheduler().runDelayed(() -> {
            if (!player.isOnline()) return;
            Location spawn = plugin.utility().spawn().loadSpawn();
            if (spawn != null) {
                plugin.core().teleports().teleport(player, spawn);
            }
        }, 1L);
    }

    private void handleMotdAndBroadcastOnJoin(Player player, boolean hasJoinedBefore, int joinIndex) {
        if (!hasJoinedBefore) {
            plugin.kit().kits().grantFirstJoinKit(player);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("unique", String.valueOf(joinIndex));

        if (!hasJoinedBefore) {
            String motd = plugin.config().motdFirstJoin();
            if (motd != null && !motd.isBlank()) {
                player.sendMessage(MessageUtil.mini(player, motd, placeholders));
            }
            String line = plugin.config().broadcastFirstJoin();
            if (line != null && !line.isBlank()) {
                Bukkit.broadcast(MessageUtil.mini(player, line, placeholders));
            }
        } else {
            fetchLuckPermsGroup(player, group -> {
                String motd = plugin.config().motdJoin(group);
                if (motd != null && !motd.isBlank()) {
                    player.sendMessage(MessageUtil.mini(player, motd, placeholders));
                }
                String msg = plugin.config().broadcastJoinFormat(group);
                if (msg != null && !msg.isBlank()) {
                    Bukkit.broadcast(MessageUtil.mini(player, msg, placeholders));
                }
            });
        }
    }

    private void handleBroadcastOnQuit(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());

        fetchLuckPermsGroup(player, group -> {
            String msg = plugin.config().broadcastLeaveFormat(group);
            if (msg != null && !msg.isBlank()) {
                Component component = MessageUtil.mini(player, msg, placeholders);
                Bukkit.broadcast(component);
            }
        });
    }

    private void fetchLuckPermsGroup(Player player, Consumer<String> callback) {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            var api = LuckPermsProvider.get();
            api.getUserManager().loadUser(player.getUniqueId()).thenAccept(user -> {
                String group = user != null ? user.getPrimaryGroup() : null;
                plugin.scheduler().runEntityTask(player, () -> callback.accept(group));
            });
        } else {
            callback.accept(null);
        }
    }
}