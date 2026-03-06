package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.module.teleport.service.RequestService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import com.ftxeven.aircore.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TeleportService {

    private final AirCore plugin;
    private final SchedulerUtil scheduler;

    private final Map<UUID, SchedulerUtil.CancellableTask> countdownTasks = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerUtil.CancellableTask> countdownMessageTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<String>> cancelHandlers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportImmunityUntil = new ConcurrentHashMap<>();

    public TeleportService(AirCore plugin, SchedulerUtil scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public Location adjustToCenter(Location loc) {
        if (plugin.config().teleportToCenter()) {
            Location centered = loc.clone();
            centered.setX(Math.floor(loc.getX()) + 0.5);
            centered.setZ(Math.floor(loc.getZ()) + 0.5);
            return centered;
        }
        return loc;
    }

    public void teleport(Player player, Location loc) {
        scheduler.runLocationTask(player.getLocation(), () ->
                player.teleportAsync(adjustToCenter(loc))
                        .thenAccept(success -> {
                            if (success) {
                                grantImmunity(player);
                            } else {
                                plugin.getLogger().warning("Async teleport failed for player " + player.getName());
                            }
                        })
        );
    }

    public void startCountdown(Player sender, Player target, Runnable action, Consumer<String> onCancel) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        clearCountdownState(senderId);
        clearCountdownState(targetId);
        activeTeleports.remove(senderId);
        activeTeleports.remove(targetId);
        cancelHandlers.remove(senderId);
        cancelHandlers.remove(targetId);

        activeTeleports.put(senderId, targetId);
        activeTeleports.put(targetId, senderId);

        if (onCancel != null) {
            cancelHandlers.put(senderId, onCancel);
            cancelHandlers.put(targetId, onCancel);
        }

        var request = plugin.teleport().requests().getRequest(targetId, senderId);
        UUID teleporteeId = (request != null && request.type() == RequestService.RequestType.TPAHERE)
                ? targetId : senderId;

        int seconds = plugin.config().teleportCountdownDuration();
        boolean repeat = plugin.config().teleportCountdownRepeat();
        boolean bypass = sender.hasPermission("aircore.bypass.teleport.countdown");

        if (seconds <= 0 || bypass) {
            clearCountdownState(senderId);
            clearCountdownState(targetId);
            action.run();
            return;
        }

        Player teleportee = plugin.getServer().getPlayer(teleporteeId);
        if (teleportee != null) {
            MessageUtil.send(teleportee, "teleport.countdown", Map.of("time", TimeUtil.formatSeconds(plugin, seconds)));
        }

        final int[] remaining = { seconds };

        if (repeat) {
            SchedulerUtil.CancellableTask msgTask = scheduler.runTimer(() -> {
                remaining[0]--;
                if (remaining[0] <= 0) return;

                Player online = plugin.getServer().getPlayer(teleporteeId);
                if (online != null && online.isOnline()) {
                    MessageUtil.send(online, "teleport.countdown", Map.of("time", TimeUtil.formatSeconds(plugin, remaining[0])));
                }
            }, 20L, 20L);

            countdownMessageTasks.put(teleporteeId, msgTask);
        }

        SchedulerUtil.CancellableTask tpTask = scheduler.runDelayed(() -> {
            clearCountdownState(senderId);
            clearCountdownState(targetId);
            action.run();
        }, seconds * 20L);

        countdownTasks.put(teleporteeId, tpTask);
    }

    public boolean hasCountdownTask(UUID uuid) {
        return countdownTasks.containsKey(uuid);
    }

    public void cancelCountdown(Player canceller, boolean cancelledByTarget) {
        if (canceller == null) return;

        UUID id = canceller.getUniqueId();
        UUID otherId = activeTeleports.remove(id);

        clearCountdownState(id);

        if (otherId != null) {
            activeTeleports.remove(otherId);
            clearCountdownState(otherId);
        }

        Consumer<String> handler = cancelHandlers.remove(id);
        if (handler == null && otherId != null) {
            handler = cancelHandlers.remove(otherId);
        }

        boolean isSelfTeleport = (otherId == null || otherId.equals(id));

        if (isSelfTeleport) {
            if (handler != null) {
                handler.accept("cancelled");
            }
            return;
        }

        Player other = plugin.getServer().getPlayer(otherId);
        if (other != null && other.isOnline()) {
            scheduler.runLocationTask(canceller.getLocation(), () -> {
                if (cancelledByTarget) {
                    MessageUtil.send(other, "teleport.lifecycle.cancelled-from", Map.of("player", canceller.getName()));
                    MessageUtil.send(canceller, "teleport.lifecycle.cancelled-to", Map.of("player", other.getName()));
                } else {
                    MessageUtil.send(canceller, "teleport.lifecycle.cancelled-from", Map.of("player", other.getName()));
                    MessageUtil.send(other, "teleport.lifecycle.cancelled-to", Map.of("player", canceller.getName()));
                }
            });
        }
    }

    private void clearCountdownState(UUID uuid) {
        SchedulerUtil.CancellableTask msgTask = countdownMessageTasks.remove(uuid);
        if (msgTask != null) msgTask.cancel();

        SchedulerUtil.CancellableTask tpTask = countdownTasks.remove(uuid);
        if (tpTask != null) tpTask.cancel();
    }

    public boolean hasCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        return countdownTasks.containsKey(uuid) || activeTeleports.containsKey(uuid);
    }

    public void grantImmunity(Player player) {
        int seconds = plugin.config().teleportImmunitySeconds();
        teleportImmunityUntil.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasImmunity(Player player) {
        Long until = teleportImmunityUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }
}