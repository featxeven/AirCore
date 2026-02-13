package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class GamemodeCommand implements TabExecutor {

    private final AirCore plugin;

    public GamemodeCommand(AirCore plugin) {
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

            if (args.length != 2) {
                sender.sendMessage("Usage: /" + label + " <gamemode> <player>");
                return true;
            }

            GameMode mode = parseMode(args[0]);
            if (mode == null) {
                sender.sendMessage("Invalid gamemode.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            setGameMode(target, mode);
            sender.sendMessage("Set gamemode for " + target.getName() + " -> " + mode.name().toLowerCase());

            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.gamemode.set-by",
                        Map.of("player", consoleName,
                                "gamemode", plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase())));
            }
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.gamemode")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.gamemode"));
            return true;
        }

        if (args.length < 1) {
            if (player.hasPermission("aircore.command.gamemode.others")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("gamemode", "others", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("gamemode", label)));
            }
            return true;
        }

        GameMode mode = parseMode(args[0]);
        if (mode == null) {
            if (player.hasPermission("aircore.command.gamemode.others")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("gamemode", "others", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("gamemode", label)));
            }
            return true;
        }

        // /gamemode <mode>
        if (args.length == 1) {
            setGameMode(player, mode);
            MessageUtil.send(player, "utilities.gamemode.set",
                    Map.of("gamemode", plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase())));
            return true;
        }

        // /gamemode <mode> <player>
        if (!player.hasPermission("aircore.command.gamemode.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.gamemode.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[1]));
            return true;
        }

        setGameMode(target, mode);

        if (target.equals(player)) {
            MessageUtil.send(player, "utilities.gamemode.set",
                    Map.of("gamemode", plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase())));
        } else {
            MessageUtil.send(player, "utilities.gamemode.set-for",
                    Map.of("gamemode", plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase()),
                            "player", target.getName()));

            MessageUtil.send(target, "utilities.gamemode.set-by",
                    Map.of("player", player.getName(),
                            "gamemode", plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase())));
        }
        return true;
    }

    private void setGameMode(Player target, GameMode mode) {
        plugin.scheduler().runEntityTask(target, () ->
                target.setGameMode(mode)
        );
    }

    private GameMode parseMode(String input) {
        return switch (input.toLowerCase()) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("aircore.command.gamemode")) {
                    return List.of();
                }
            }

            return Stream.of("survival", "creative", "adventure", "spectator")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            if (!(sender instanceof Player) || sender.hasPermission("aircore.command.gamemode.others")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .limit(20)
                        .toList();
            }
        }

        return List.of();
    }
}