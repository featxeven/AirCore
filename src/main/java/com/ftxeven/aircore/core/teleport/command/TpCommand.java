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
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tp"));
            return true;
        }

        if (args.length < 1) {
            String usageKey = player.hasPermission("aircore.command.tp.others")
                    ? plugin.config().getUsage("tp", "others", label)
                    : plugin.config().getUsage("tp", label);
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usageKey));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        // /tp <player>
        if (args.length == 1) {
            plugin.core().teleports().teleport(player, target.getLocation());
            MessageUtil.send(player, "teleport.direct.to-player",
                    Map.of("player", target.getName()));
            return true;
        }

        // /tp <player> <otherplayer>
        if (!player.hasPermission("aircore.command.tp.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tp.others"));
            return true;
        }

        Player other = Bukkit.getPlayerExact(args[1]);
        if (other == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[1]));
            return true;
        }

        // executor == target
        if (target.equals(player)) {
            plugin.core().teleports().teleport(player, other.getLocation());
            MessageUtil.send(player, "teleport.direct.to-player",
                    Map.of("player", other.getName()));
            return true;
        }

        // executor == other
        if (other.equals(player)) {
            plugin.core().teleports().teleport(target, player.getLocation());
            MessageUtil.send(player, "teleport.direct.player-to-self",
                    Map.of("player", target.getName()));
            return true;
        }

        // executor different from both
        plugin.core().teleports().teleport(target, other.getLocation());
        MessageUtil.send(player, "teleport.direct.player-to-target",
                Map.of("player", target.getName(), "target", other.getName()));
        MessageUtil.send(target, "teleport.direct.to-player-by",
                Map.of("player", player.getName(), "target", other.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("aircore.command.tp")) return List.of();

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        if (args.length == 2 && player.hasPermission("aircore.command.tp.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }
}
