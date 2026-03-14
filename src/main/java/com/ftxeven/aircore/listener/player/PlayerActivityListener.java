package com.ftxeven.aircore.listener.player;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import com.ftxeven.aircore.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

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

        boolean hasTeleport = plugin.config().cancelTeleportOnMove() && plugin.core().teleports().hasCountdownTask(uuid);
        boolean isAfk = plugin.utility().afk().isAfk(uuid);

        if (!hasTeleport && !isAfk) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        boolean worldChanged = !from.getWorld().equals(to.getWorld());
        boolean blockChanged = worldChanged || (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ());

        if (!blockChanged) return;

        if (hasTeleport) {
            plugin.core().teleports().cancelCountdown(player, true);
        }

        if (isAfk) {
            long elapsed = plugin.utility().afk().clearAfk(uuid);
            String timeStr = TimeUtil.formatSeconds(plugin, elapsed);

            scheduler.runLocationTask(to, () -> {
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
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.GOD)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.core().teleports().hasImmunity(player)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.config().cancelTeleportOnDamage() && plugin.core().teleports().hasCountdownTask(uuid)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.utility().afk().isAfk(uuid)) {
            long elapsed = plugin.utility().afk().clearAfk(uuid);
            String timeStr = TimeUtil.formatSeconds(plugin, elapsed);
            scheduler.runLocationTask(player.getLocation(), () -> MessageUtil.send(player, "utilities.afk.stop", Map.of("time", timeStr)));
        }

        if (plugin.config().cancelTeleportOnCommand() && plugin.core().teleports().hasCountdownTask(uuid)) {
            plugin.core().teleports().cancelCountdown(player, false);
        }

        String rawMessage = event.getMessage().substring(1);
        if (rawMessage.isEmpty()) return;

        if (player.hasPermission("aircore.bypass.command.*")) return;

        long remaining = plugin.core().commandCooldowns().getRemaining(player, rawMessage);

        if (remaining > 0) {
            event.setCancelled(true);
            String formatted = TimeUtil.formatSeconds(plugin, remaining);

            scheduler.runLocationTask(player.getLocation(), () ->
                    MessageUtil.send(player, "errors.command-cooldown", Map.of(
                            "time", formatted
                    ))
            );
            return;
        }

        plugin.core().commandCooldowns().apply(uuid, rawMessage);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.config().cancelTeleportOnInteract() && plugin.core().teleports().hasCountdownTask(player.getUniqueId())) {
            plugin.core().teleports().cancelCountdown(player, false);
        }
    }
}