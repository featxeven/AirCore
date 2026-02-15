package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
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

public final class TpDenyCommand implements TabExecutor {

    private final AirCore plugin;

    public TpDenyCommand(AirCore plugin) {
        this.plugin = plugin;
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

        if (!target.hasPermission("aircore.command.tpdeny")) {
            MessageUtil.send(target, "errors.no-permission", Map.of("permission", "aircore.command.tpdeny"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("@a")) {
            handleDenyAll(target);
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(target, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("tpdeny", label)));
            return true;
        }

        RequestService.TeleportRequest req;
        Player senderPlayer;

        if (args.length == 0) {
            req = plugin.teleport().requests().getRequest(target.getUniqueId(), null);
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
                return true;
            }

            senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer == null || req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                plugin.teleport().requests().popLatest(target.getUniqueId());
                plugin.teleport().cooldowns().clear(target.getUniqueId());
                return true;
            }

            plugin.teleport().requests().popLatest(target.getUniqueId());
            plugin.teleport().cooldowns().clear(target.getUniqueId());
        } else {
            senderPlayer = Bukkit.getPlayerExact(args[0]);
            if (senderPlayer == null) {
                MessageUtil.send(target, "errors.player-not-found", Map.of("player", args[0]));
                return true;
            }

            req = plugin.teleport().requests().getRequest(target.getUniqueId(), senderPlayer.getUniqueId());
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-request-from", Map.of("player", senderPlayer.getName()));
                return true;
            }

            if (req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                plugin.teleport().requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
                plugin.teleport().cooldowns().clear(target.getUniqueId());
                return true;
            }

            plugin.teleport().requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
            plugin.teleport().cooldowns().clear(target.getUniqueId());
        }

        MessageUtil.send(target, "teleport.actions.denied-player", Map.of("player", req.senderName()));
        MessageUtil.send(senderPlayer, "teleport.actions.denied-from", Map.of("player", req.targetName()));
        return true;
    }

    private void handleDenyAll(Player target) {
        if (!target.hasPermission("aircore.command.tpdeny.all")) {
            MessageUtil.send(target, "errors.no-permission", Map.of("permission", "aircore.command.tpdeny.all"));
            return;
        }

        if (!plugin.teleport().requests().hasRequests(target.getUniqueId())) {
            MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
            return;
        }

        Deque<RequestService.TeleportRequest> queue = plugin.teleport().requests().getAllRequests(target.getUniqueId());
        for (RequestService.TeleportRequest req : queue) {
            Player senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer != null) {
                MessageUtil.send(senderPlayer, "teleport.actions.denied-from", Map.of("player", req.targetName()));
            }
        }

        plugin.teleport().requests().clearRequestsForTarget(target.getUniqueId());
        plugin.teleport().cooldowns().clear(target.getUniqueId());
        MessageUtil.send(target, "teleport.actions.denied-all", Map.of());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();

        String input = args[0].toLowerCase();
        Stream<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName);

        if (player.hasPermission("aircore.command.tpdeny.all")) {
            names = Stream.concat(names, Stream.of("@a"));
        }

        return names.filter(name -> name.toLowerCase().startsWith(input)).limit(20).toList();
    }
}