package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
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
import java.util.function.Consumer;

import static org.bukkit.Bukkit.getServer;

public final class PlayerLifecycleListener implements Listener {

    private final AirCore plugin;
    private final Set<UUID> justDied = new HashSet<>();
    private final Set<UUID> dirtyPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final int BATCH_SIZE = 50;
    private static final long AUTOSAVE_INTERVAL_TICKS = 2400L;

    public PlayerLifecycleListener(AirCore plugin) {
        this.plugin = plugin;
        startInventoryAutoSave();
    }

    private void startInventoryAutoSave() {
        plugin.scheduler().runAsyncTimer(() -> {
            Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
            if (allPlayers.isEmpty()) {
                return;
            }

            for (Player player : allPlayers) {
                dirtyPlayers.add(player.getUniqueId());
            }

            List<Player> playersToSave = new ArrayList<>(dirtyPlayers.size());
            for (UUID uuid : dirtyPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    playersToSave.add(player);
                }
            }

            if (playersToSave.isEmpty()) {
                dirtyPlayers.clear();
                return;
            }

            for (int i = 0; i < playersToSave.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, playersToSave.size());
                List<Player> batch = playersToSave.subList(i, endIndex);

                try {
                    plugin.database().inventories().saveAllSync(batch);

                    for (Player player : batch) {
                        dirtyPlayers.remove(player.getUniqueId());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to auto-save inventory batch: " + e.getMessage());
                }
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

        boolean hasJoinedBefore = plugin.database().records().hasJoinedBefore(uuid);

        plugin.core().commandCooldowns().load(uuid);

        plugin.scheduler().runAsync(() -> {
            boolean chat = plugin.database().records().getToggle(uuid, ToggleService.Toggle.CHAT.getColumn());
            boolean mentions = plugin.database().records().getToggle(uuid, ToggleService.Toggle.MENTIONS.getColumn());
            boolean pm = plugin.database().records().getToggle(uuid, ToggleService.Toggle.PM.getColumn());
            boolean socialspy = plugin.database().records().getToggle(uuid, ToggleService.Toggle.SOCIALSPY.getColumn());
            boolean pay = plugin.database().records().getToggle(uuid, ToggleService.Toggle.PAY.getColumn());
            boolean teleport = plugin.database().records().getToggle(uuid, ToggleService.Toggle.TELEPORT.getColumn());
            boolean god = plugin.database().records().getToggle(uuid, ToggleService.Toggle.GOD.getColumn());
            boolean flyEnabled = plugin.database().records().getToggle(uuid, ToggleService.Toggle.FLY.getColumn());
            double speed = plugin.database().records().getSpeed(uuid);

            var block = plugin.database().blocks().load(uuid);
            double balance = plugin.database().records().getBalance(uuid);
            var homes = plugin.database().homes().load(uuid);
            var bundle = plugin.database().inventories().loadAllInventory(uuid);

            // Apply data in a single entity task before spawn teleport
            plugin.scheduler().runEntityTask(player, () -> {
                if (!player.isOnline()) return;

                // Apply toggles
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.CHAT, chat);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.MENTIONS, mentions);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.PM, pm);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.SOCIALSPY, socialspy);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.PAY, pay);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.TELEPORT, teleport);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.GOD, god);
                plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.FLY, flyEnabled);

                float walkSpeed = (float) Math.min(Math.max(speed * 0.2, 0.0), 1.0);
                float flySpeed = (float) Math.min(Math.max(speed * 0.1, 0.0), 1.0);

                player.setWalkSpeed(walkSpeed);
                player.setFlySpeed(flySpeed);

                GameMode gameMode = player.getGameMode();

                // Handle flight based on toggle
                if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
                    player.setAllowFlight(true);
                } else {
                    player.setAllowFlight(flyEnabled);
                    player.setFlying(false);
                }

                block.forEach(target -> plugin.core().blocks().block(uuid, target));
                plugin.economy().balances().setBalanceLocal(uuid, balance);
                plugin.home().homes().loadFromDatabase(uuid, homes);

                // Restore inventory + enderchest
                if (bundle != null) {
                    player.getInventory().setContents(bundle.contents());
                    player.getInventory().setArmorContents(bundle.armor());
                    if (bundle.offhand() != null) player.getInventory().setItemInOffHand(bundle.offhand());
                    player.getEnderChest().setContents(bundle.enderChest());
                    player.updateInventory();
                }

                if (!hasJoinedBefore && plugin.config().teleportToSpawnOnFirstJoin()) {
                    teleportToSpawn(player);
                } else if (plugin.config().teleportToSpawnOnJoin()) {
                    teleportToSpawn(player);
                }
            });
        });

        handleMotdAndBroadcastOnJoin(player, uuid, hasJoinedBefore);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        event.quitMessage(null);
        justDied.remove(uuid);
        dirtyPlayers.remove(uuid);

        // Cancel any active teleport countdowns
        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        try {
            plugin.database().records().setLocation(uuid, player.getLocation());
            plugin.database().inventories().saveAllSync(Collections.singleton(player));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save player data for " + player.getName() + ": " + e.getMessage());
        }

        try {
            plugin.economy().balances().unloadBalance(uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unload balance for " + player.getName() + ": " + e.getMessage());
        }

        if (!plugin.config().retainRequestStateOnLogout()) {
            try {
                plugin.teleport().requests().clearRequests(uuid);
                plugin.teleport().cooldowns().clear(uuid);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to clear teleport requests for " + player.getName() + ": " + e.getMessage());
            }
        }

        try {
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
        if (!plugin.scheduler().isFoliaServer()) return;

        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        justDied.add(uuid);

        plugin.utility().back().setLastDeath(uuid, player.getLocation());

        if (plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
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

    private void handleMotdAndBroadcastOnJoin(Player player, UUID uuid, boolean hasJoinedBefore) {
        int joinIndex;
        if (!hasJoinedBefore) {
            joinIndex = plugin.database().records().createPlayerRecord(uuid, player.getName());
            plugin.kit().kits().grantFirstJoinKit(player);
        } else {
            Integer idx = plugin.database().records().getJoinIndex(uuid);
            joinIndex = idx != null ? idx : 0;
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
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            var api = LuckPermsProvider.get();
            api.getUserManager().loadUser(player.getUniqueId()).thenAccept(user -> {
                String group = user != null ? user.getPrimaryGroup() : null;
                plugin.scheduler().runTask(() -> callback.accept(group));
            });
        } else {
            callback.accept(null);
        }
    }
}