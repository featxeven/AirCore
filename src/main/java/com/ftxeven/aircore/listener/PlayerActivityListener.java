package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import com.ftxeven.aircore.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public final class PlayerActivityListener implements Listener {

    private final AirCore plugin;
    private final SchedulerUtil scheduler;

    public PlayerActivityListener(AirCore plugin, SchedulerUtil scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check teleport cancellation
        if (plugin.config().cancelTeleportOnMove() && plugin.core().teleports().hasCountdown(player)) {
            int fromBlockX = event.getFrom().getBlockX();
            int fromBlockY = event.getFrom().getBlockY();
            int fromBlockZ = event.getFrom().getBlockZ();

            int toBlockX = event.getTo().getBlockX();
            int toBlockY = event.getTo().getBlockY();
            int toBlockZ = event.getTo().getBlockZ();

            // Check if world changed or block coordinates changed
            boolean worldChanged = !event.getFrom().getWorld().equals(event.getTo().getWorld());
            boolean blockChanged = (fromBlockX != toBlockX || fromBlockY != toBlockY || fromBlockZ != toBlockZ);

            if (worldChanged || blockChanged) {
                // Cancel countdown
                plugin.core().teleports().cancelCountdown(player, true);
            }
        }

        // Clear AFK state if moving
        if (plugin.utility().afk().isAfk(uuid)) {
            int fromBlockX = event.getFrom().getBlockX();
            int fromBlockY = event.getFrom().getBlockY();
            int fromBlockZ = event.getFrom().getBlockZ();

            int toBlockX = event.getTo().getBlockX();
            int toBlockY = event.getTo().getBlockY();
            int toBlockZ = event.getTo().getBlockZ();

            boolean blockChanged = (fromBlockX != toBlockX || fromBlockY != toBlockY || fromBlockZ != toBlockZ);

            if (blockChanged) {
                long elapsed = plugin.utility().afk().clearAfk(uuid);
                String timeStr = TimeUtil.formatSeconds(plugin, elapsed);

                // Send message
                scheduler.runLocationTask(player.getLocation(), () -> {
                    MessageUtil.send(player, "utilities.afk.stop", Map.of("time", timeStr));

                    // Notify other players
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (!other.equals(player) && other.hasPermission("aircore.command.afk.notify")) {
                            scheduler.runLocationTask(other.getLocation(), () ->
                                    MessageUtil.send(other, "utilities.afk.stop-notify", Map.of(
                                            "player", player.getName(),
                                            "time", timeStr
                                    ))
                            );
                        }
                    }
                });
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // God toggle
        if (plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.GOD)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.core().teleports().hasImmunity(player)) {
            event.setCancelled(true);
            return;
        }

        // Cancel teleport countdown
        if (plugin.config().cancelTeleportOnDamage() && plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Clear AFK and notify
        if (plugin.utility().afk().isAfk(uuid)) {
            long elapsed = plugin.utility().afk().clearAfk(uuid);
            String timeStr = TimeUtil.formatSeconds(plugin, elapsed);

            scheduler.runLocationTask(player.getLocation(), () -> {
                MessageUtil.send(player, "utilities.afk.stop", Map.of("time", timeStr));

                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(player) && other.hasPermission("aircore.command.afk.notify")) {
                        scheduler.runLocationTask(other.getLocation(), () ->
                                MessageUtil.send(other, "utilities.afk.stop-notify", Map.of(
                                        "player", player.getName(),
                                        "time", timeStr
                                ))
                        );
                    }
                }
            });
        }

        String[] parts = event.getMessage().substring(1).split(" ");
        String base = parts[0].toLowerCase();

        // Cancel teleport countdown
        if (plugin.config().cancelTeleportOnCommand() && plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        // Build key from the entire command line
        String key;
        if (parts.length > 1) {
            key = base + " " + String.join(" ",
                    Arrays.copyOfRange(parts, 1, parts.length)).toLowerCase();
        } else {
            key = base;
        }

        if (player.hasPermission("aircore.bypass.command.cooldown")) {
            return;
        }

        // Lookup cooldown
        int seconds = plugin.config().commandCooldown(key);
        if (seconds > 0) {
            var cooldowns = plugin.core().commandCooldowns();

            if (cooldowns.isOnCooldown(uuid, key)) {
                long remaining = cooldowns.getRemaining(uuid, key);
                String formatted = TimeUtil.formatSeconds(plugin, remaining);

                scheduler.runLocationTask(player.getLocation(), () ->
                        MessageUtil.send(player, "errors.command-cooldown", Map.of(
                                "command", key,
                                "time", formatted
                        ))
                );

                event.setCancelled(true);
                return;
            }

            // Apply new cooldown
            cooldowns.apply(uuid, key, seconds);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.config().cancelTeleportOnInteract() && plugin.core().teleports().hasCountdown(player)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }
    }
}