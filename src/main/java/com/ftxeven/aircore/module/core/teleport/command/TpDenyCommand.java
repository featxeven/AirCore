package com.ftxeven.aircore.module.core.teleport.command;

import com.ftxeven.aircore.module.core.teleport.service.RequestService;
import com.ftxeven.aircore.module.core.teleport.TeleportManager;
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

    private final TeleportManager manager;

    public TpDenyCommand(TeleportManager manager) {
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

        if (!target.hasPermission("aircore.command.tpdeny")) {
            MessageUtil.send(target, "errors.no-permission",
                    Map.of("permission", "aircore.command.tpdeny"));
            return true;
        }

        // Handle @a (deny all)
        if (args.length == 1 && args[0].equalsIgnoreCase("@a")) {
            if (!target.hasPermission("aircore.command.tpdeny.all")) {
                MessageUtil.send(target, "errors.player-not-found", Map.of("player", "@a"));
                return true;
            }

            if (!manager.requests().hasRequests(target.getUniqueId())) {
                MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
                return true;
            }

            Deque<RequestService.TeleportRequest> queue =
                    manager.requests().getAllRequests(target.getUniqueId());

            for (RequestService.TeleportRequest req : queue) {
                Player senderPlayer = Bukkit.getPlayer(req.sender());
                if (senderPlayer != null) {
                    MessageUtil.send(senderPlayer, "teleport.actions.denied-from",
                            Map.of("player", req.targetName()));
                }
            }

            manager.requests().clearRequestsForTarget(target.getUniqueId());
            manager.cooldowns().clear(target.getUniqueId());
            MessageUtil.send(target, "teleport.actions.denied-all", Map.of());
            return true;
        }

        RequestService.TeleportRequest req;
        Player senderPlayer;

        if (args.length == 0) {
            req = manager.requests().getRequest(target.getUniqueId(), null);
            if (req == null) {
                MessageUtil.send(target, "teleport.errors.no-requests", Map.of());
                return true;
            }

            if (req.expiryTime() < System.currentTimeMillis()) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from",
                        Map.of("player", req.senderName()));
                manager.requests().popLatest(target.getUniqueId());
                manager.cooldowns().clear(target.getUniqueId());
                return true;
            }

            senderPlayer = Bukkit.getPlayer(req.sender());
            if (senderPlayer == null) {
                MessageUtil.send(target, "teleport.lifecycle.expired-from",
                        Map.of("player", req.senderName()));
                manager.requests().popLatest(target.getUniqueId());
                manager.cooldowns().clear(target.getUniqueId());
                return true;
            }

            MessageUtil.send(target, "teleport.actions.denied-player",
                    Map.of("player", req.senderName()));
            MessageUtil.send(senderPlayer, "teleport.actions.denied-from",
                    Map.of("player", req.targetName()));
            manager.requests().popLatest(target.getUniqueId());
            manager.cooldowns().clear(target.getUniqueId());
            return true;
        }

        String senderName = args[0];
        senderPlayer = Bukkit.getPlayerExact(senderName);
        if (senderPlayer == null) {
            MessageUtil.send(target, "errors.player-not-found", Map.of("player", senderName));
            return true;
        }

        req = manager.requests().getRequest(target.getUniqueId(), senderPlayer.getUniqueId());
        if (req == null) {
            MessageUtil.send(target, "teleport.errors.no-request-from",
                    Map.of("player", senderPlayer.getName()));
            return true;
        }

        if (req.expiryTime() < System.currentTimeMillis()) {
            MessageUtil.send(target, "teleport.lifecycle.expired-from",
                    Map.of("player", req.senderName()));
            manager.requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
            manager.cooldowns().clear(target.getUniqueId());
            return true;
        }

        MessageUtil.send(target, "teleport.actions.denied-player",
                Map.of("player", req.senderName()));
        MessageUtil.send(senderPlayer, "teleport.actions.denied-from",
                Map.of("player", req.targetName()));
        manager.requests().popRequestFrom(target.getUniqueId(), senderPlayer.getUniqueId());
        manager.cooldowns().clear(target.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        Stream<String> names = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName);

        if (player.hasPermission("aircore.command.tpdeny.all")) {
            names = Stream.concat(names, Stream.of("@a"));
        }

        return names.filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}
