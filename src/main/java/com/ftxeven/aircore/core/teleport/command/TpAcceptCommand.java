package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.teleport.TeleportManager;
import com.ftxeven.aircore.core.teleport.service.RequestService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class TpAcceptCommand implements TabExecutor {

    private final AirCore plugin;
    private final TeleportManager manager;

    public TpAcceptCommand(AirCore plugin, TeleportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player target)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!target.hasPermission("aircore.command.tpaccept")) {
            MessageUtil.send(target, "errors.no-permission", Map.of("permission", "aircore.command.tpaccept"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("@a")) {
            handleAcceptAll(target);
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(target, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("tpaccept", label)));
            return true;
        }

        Player senderPlayer;
        RequestService.TeleportRequest req;

        if (args.length == 0) {
            req = manager.requests().getRequest(target.getUniqueId(), null);
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
                return true;
            }

            senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer == null || req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                manager.requests().popLatest(target.getUniqueId());
                manager.cooldowns().clear(target.getUniqueId());
                return true;
            }
            manager.requests().popLatest(target.getUniqueId());
            manager.cooldowns().clear(target.getUniqueId());
        } else {
            senderPlayer = Bukkit.getPlayerExact(args[0]);
            if (senderPlayer == null) {
                MessageUtil.send(target, "errors.player-not-found", Map.of("player", args[0]));
                return true;
            }

            req = manager.requests().getRequest(target.getUniqueId(), senderPlayer.getUniqueId());
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-request-from", Map.of("player", senderPlayer.getName()));
                return true;
            }

            if (req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                manager.requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
                return true;
            }
            manager.requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
        }

        processRequest(req, senderPlayer, target);
        return true;
    }

    private void handleAcceptAll(Player target) {
        if (!target.hasPermission("aircore.command.tpaccept.all")) {
            MessageUtil.send(target, "errors.no-permission", Map.of("permission", "aircore.command.tpaccept.all"));
            return;
        }

        Deque<RequestService.TeleportRequest> queue = manager.requests().getAllRequests(target.getUniqueId());
        if (queue.isEmpty()) {
            MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
            return;
        }

        for (RequestService.TeleportRequest req : queue) {
            if (req.expiryTime() < System.currentTimeMillis()) continue;
            Player senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer != null) {
                processRequest(req, senderPlayer, target);
            }
        }

        manager.requests().clearRequestsForTarget(target.getUniqueId());
        manager.cooldowns().clear(target.getUniqueId());
        MessageUtil.send(target, "teleport.actions.accepted-all", Map.of());
    }

    private void processRequest(RequestService.TeleportRequest req, Player senderPlayer, Player target) {
        MessageUtil.send(target, "teleport.actions.accepted-player", Map.of("player", req.senderName()));
        MessageUtil.send(senderPlayer, "teleport.actions.accepted-from", Map.of("player", req.targetName()));

        if (req.type() == RequestService.RequestType.TPA) {
            plugin.core().teleports().startCountdown(senderPlayer, target, () -> {
                plugin.core().teleports().teleport(senderPlayer, target.getLocation());
                MessageUtil.send(senderPlayer, "teleport.direct.to-player", Map.of("player", target.getName()));
            }, cancelReason -> MessageUtil.send(senderPlayer, "teleport.lifecycle.cancelled-to", Map.of("player", target.getName())));
        } else {
            plugin.core().teleports().startCountdown(target, senderPlayer, () -> {
                plugin.core().teleports().teleport(target, senderPlayer.getLocation());
                MessageUtil.send(target, "teleport.direct.to-player", Map.of("player", senderPlayer.getName()));
            }, cancelReason -> MessageUtil.send(target, "teleport.lifecycle.cancelled-to", Map.of("player", senderPlayer.getName())));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        Stream<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        if (player.hasPermission("aircore.command.tpaccept.all")) {
            names = Stream.concat(names, Stream.of("@a"));
        }

        return names.filter(name -> name.toLowerCase().startsWith(input)).limit(20).toList();
    }
}