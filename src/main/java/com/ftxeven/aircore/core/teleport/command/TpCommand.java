package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class TpCommand implements TabExecutor {

    private final AirCore plugin;

    public TpCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.tp")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.tp"));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player, label);
            return true;
        }

        if (args.length >= 2 && !player.hasPermission("aircore.command.tp.others")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.tp.others"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", getUsageString(player, label)));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        if (args.length == 1) {
            handleSingleTeleport(player, target);
            return true;
        }

        Player other = Bukkit.getPlayerExact(args[1]);
        if (other == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[1]));
            return true;
        }

        handleDoubleTeleport(player, target, other);
        return true;
    }

    private void handleSingleTeleport(Player player, Player target) {
        plugin.core().teleports().teleport(player, target.getLocation());
        MessageUtil.send(player, "teleport.direct.to-player", Map.of("player", target.getName()));
    }

    private void handleDoubleTeleport(Player player, Player target, Player other) {
        if (target.equals(player)) {
            plugin.core().teleports().teleport(player, other.getLocation());
            MessageUtil.send(player, "teleport.direct.to-player", Map.of("player", other.getName()));
            return;
        }

        if (other.equals(player)) {
            plugin.core().teleports().teleport(target, player.getLocation());
            MessageUtil.send(player, "teleport.direct.player-to-self", Map.of("player", target.getName()));
            return;
        }

        plugin.core().teleports().teleport(target, other.getLocation());
        MessageUtil.send(player, "teleport.direct.player-to-target",
                Map.of("player", target.getName(), "target", other.getName()));
        MessageUtil.send(target, "teleport.direct.to-player-by",
                Map.of("player", player.getName(), "target", other.getName()));
    }

    private void sendUsage(Player player, String label) {
        MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", getUsageString(player, label)));
    }

    private String getUsageString(Player player, String label) {
        return player.hasPermission("aircore.command.tp.others")
                ? plugin.config().getUsage("tp", "others", label)
                : plugin.config().getUsage("tp", label);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("aircore.command.tp")) return List.of();

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1 || (args.length == 2 && player.hasPermission("aircore.command.tp.others"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }
}