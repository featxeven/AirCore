package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <gamemode> <player>");
                return true;
            }
            handleGamemode(sender, args[0], args[1]);
            return true;
        }

        if (!player.hasPermission("aircore.command.gamemode")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.gamemode"));
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.gamemode.others");

        if (args.length == 0) {
            sendUsage(player, "incorrect-usage", label);
            return true;
        }

        if (!hasOthers && args.length > 1) {
            sendUsage(player, "too-many-arguments", label);
            return true;
        }

        if (args.length > 2) {
            sendUsage(player, "too-many-arguments", label);
            return true;
        }

        GameMode mode = parseMode(args[0]);
        if (mode == null) {
            sendUsage(player, "incorrect-usage", label);
            return true;
        }

        String targetName = (args.length == 1) ? player.getName() : args[1];

        if (!targetName.equalsIgnoreCase(player.getName()) && !hasOthers) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.gamemode.others"));
            return true;
        }

        handleGamemode(player, args[0], targetName);
        return true;
    }

    private void handleGamemode(CommandSender sender, String modeInput, String targetName) {
        GameMode mode = parseMode(modeInput);
        if (mode == null) return;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "errors.player-not-found", Map.of());
            } else {
                sender.sendMessage("Player not found");
            }
            return;
        }

        plugin.scheduler().runEntityTask(target, () -> target.setGameMode(mode));

        String modeName = String.valueOf(plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase()));
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (sender instanceof Player p) {
            if (target.equals(p)) {
                MessageUtil.send(p, "utilities.gamemode.set", Map.of("gamemode", modeName));
            } else {
                MessageUtil.send(p, "utilities.gamemode.set-for", Map.of("gamemode", modeName, "player", target.getName()));
                MessageUtil.send(target, "utilities.gamemode.set-by", Map.of("player", p.getName(), "gamemode", modeName));
            }
        } else {
            sender.sendMessage("Set gamemode for " + target.getName() + " -> " + mode.name().toLowerCase());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.gamemode.set-by", Map.of("player", senderName, "gamemode", modeName));
            }
        }
    }

    private GameMode parseMode(String input) {
        String lower = input.toLowerCase();

        if (lower.equals(plugin.commandConfig().getSelector("gamemode", "survival"))) return GameMode.SURVIVAL;
        if (lower.equals(plugin.commandConfig().getSelector("gamemode", "creative"))) return GameMode.CREATIVE;
        if (lower.equals(plugin.commandConfig().getSelector("gamemode", "adventure"))) return GameMode.ADVENTURE;
        if (lower.equals(plugin.commandConfig().getSelector("gamemode", "spectator"))) return GameMode.SPECTATOR;

        return switch (lower) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private void sendUsage(Player player, String type, String label) {
        String variant = player.hasPermission("aircore.command.gamemode.others") ? "others" : null;
        MessageUtil.send(player, "errors." + type, Map.of("usage", plugin.commandConfig().getUsage("gamemode", variant, label)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender instanceof Player player && !player.hasPermission("aircore.command.gamemode")) {
                return Collections.emptyList();
            }

            return Stream.of(
                    plugin.commandConfig().getSelector("gamemode", "survival"),
                    plugin.commandConfig().getSelector("gamemode", "creative"),
                    plugin.commandConfig().getSelector("gamemode", "adventure"),
                    plugin.commandConfig().getSelector("gamemode", "spectator")
            ).filter(opt -> opt != null && opt.startsWith(input)).toList();
        }

        if (args.length == 2) {
            if (sender instanceof Player player && !player.hasPermission("aircore.command.gamemode.others")) {
                return Collections.emptyList();
            }
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20).toList();
        }

        return Collections.emptyList();
    }
}