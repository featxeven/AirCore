package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FeedCommand implements TabExecutor {

    private final AirCore plugin;

    public FeedCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console execution
        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player|@a>");
                return true;
            }

            if (args[0].equalsIgnoreCase("@a")) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    feedPlayer(target);
                    if (plugin.config().consoleToPlayerFeedback()) {
                        MessageUtil.send(target, "utilities.feed.by", Map.of("player", consoleName));
                    }
                }
                sender.sendMessage("All players have been fed.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            feedPlayer(target);
            sender.sendMessage("Fed " + target.getName());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.feed.by", Map.of("player", consoleName));
            }
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.feed")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.feed"));
            return true;
        }

        // /feed (self)
        if (args.length == 0) {
            feedPlayer(player);
            MessageUtil.send(player, "utilities.feed.self", Map.of());
            return true;
        }

        // /feed @a
        if (args[0].equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.feed.all")) {
                MessageUtil.send(player, "errors.player-not-found",
                        Map.of("player", "@a"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                feedPlayer(target);
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.feed.by",
                            Map.of("player", player.getName()));
                }
            }
            MessageUtil.send(player, "utilities.feed.everyone", Map.of());
            return true;
        }

        // /feed <player>
        if (!player.hasPermission("aircore.command.feed.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.feed.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found",
                    Map.of("player", args[0]));
            return true;
        }

        if (target.equals(player)) {
            feedPlayer(player);
            MessageUtil.send(player, "utilities.feed.self", Map.of());
            return true;
        }

        feedPlayer(target);

        MessageUtil.send(player, "utilities.feed.for",
                Map.of("player", target.getName()));

        MessageUtil.send(target, "utilities.feed.by",
                Map.of("player", player.getName()));

        return true;
    }

    private void feedPlayer(Player target) {
        plugin.scheduler().runEntityTask(target, () -> {
            target.setFoodLevel(20);
            target.setSaturation(20f);
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.feed")) {
                return List.of();
            }
        }

        List<String> suggestions = List.of();

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.feed.others")) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.feed.all")) {
            if ("@a".startsWith(input)) {
                suggestions = new ArrayList<>(suggestions);
                suggestions.add("@a");
            }
        }

        return suggestions;
    }
}