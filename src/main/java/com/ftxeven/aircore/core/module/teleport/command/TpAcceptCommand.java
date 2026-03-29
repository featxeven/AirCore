package com.ftxeven.aircore.core.module.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.module.teleport.service.RequestService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class TpAcceptCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.tpaccept";
    private static final String PERM_ALL = "aircore.command.tpaccept.all";

    public TpAcceptCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!target.hasPermission(PERM_BASE)) {
            MessageUtil.send(target, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(target, "errors.too-many-arguments", Map.of("usage", plugin.commandConfig().getUsage("tpaccept", label)));
            return true;
        }

        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        UUID targetId = target.getUniqueId();

        if (args.length > 0 && args[0].equalsIgnoreCase(selectorAll)) {
            if (!target.hasPermission(PERM_ALL)) {
                MessageUtil.send(target, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
            handleAcceptAll(target);
            return true;
        }

        Player senderPlayer;
        RequestService.TeleportRequest req;

        if (args.length == 0) {
            req = plugin.teleport().requests().getRequest(targetId, null);
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
                return true;
            }

            senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer == null || req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                plugin.teleport().requests().popLatest(targetId);
                return true;
            }
            plugin.teleport().requests().popLatest(targetId);
        } else {
            senderPlayer = Bukkit.getPlayerExact(args[0]);
            if (senderPlayer == null) {
                MessageUtil.send(target, "errors.player-not-found", Map.of());
                return true;
            }

            req = plugin.teleport().requests().getRequest(targetId, senderPlayer.getUniqueId());
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-request-from", Map.of("player", senderPlayer.getName()));
                return true;
            }

            if (req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from", Map.of("player", req.senderName()));
                plugin.teleport().requests().popRequestFrom(targetId, senderPlayer.getUniqueId());
                return true;
            }
            plugin.teleport().requests().popRequestFrom(targetId, senderPlayer.getUniqueId());
        }

        processRequest(req, senderPlayer, target);
        return true;
    }

    private void handleAcceptAll(Player target) {
        UUID targetId = target.getUniqueId();
        Deque<RequestService.TeleportRequest> queue = plugin.teleport().requests().getAllRequests(targetId);

        if (queue == null || queue.isEmpty()) {
            MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
            return;
        }

        long now = System.currentTimeMillis();
        for (RequestService.TeleportRequest req : queue) {
            if (req.expiryTime() < now) continue;
            Player senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer != null) {
                processRequest(req, senderPlayer, target);
            }
        }

        plugin.teleport().requests().clearRequestsForTarget(targetId);
        MessageUtil.send(target, "teleport.actions.accepted-all", Map.of());
    }

    private void processRequest(RequestService.TeleportRequest req, Player senderPlayer, Player target) {
        MessageUtil.send(target, "teleport.actions.accepted-player", Map.of("player", req.senderName()));
        MessageUtil.send(senderPlayer, "teleport.actions.accepted-from", Map.of("player", req.targetName()));

        if (req.type() == RequestService.RequestType.TPA) {
            plugin.core().teleports().startCountdown(senderPlayer, target, () -> {
                Location loc = plugin.core().teleports().adjustToCenter(target.getLocation());
                plugin.core().teleports().teleport(senderPlayer, loc);
                MessageUtil.send(senderPlayer, "teleport.direct.to-player", Map.of("player", target.getName()));
            }, null);
        } else {
            plugin.core().teleports().startCountdown(target, senderPlayer, () -> {
                Location loc = plugin.core().teleports().adjustToCenter(senderPlayer.getLocation());
                plugin.core().teleports().teleport(target, loc);
                MessageUtil.send(target, "teleport.direct.to-player", Map.of("player", senderPlayer.getName()));
            }, null);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .limit(20)
                .forEach(suggestions::add);

        if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
            suggestions.add(selectorAll);
        }

        return suggestions;
    }
}