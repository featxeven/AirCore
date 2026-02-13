package com.ftxeven.aircore.core.utility.command;

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

        if (args.length == 0) {
            String usageKey = player.hasPermission("aircore.command.gamemode.others") ? "others" : null;
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("gamemode", usageKey, label)));
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.gamemode.others");

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            String usageKey = hasOthers ? "others" : null;
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("gamemode", usageKey, label)));
            return true;
        }

        GameMode mode = parseMode(args[0]);
        if (mode == null) {
            String usageKey = hasOthers ? "others" : null;
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("gamemode", usageKey, label)));
            return true;
        }

        if (args.length == 1) {
            handleGamemode(player, args[0], player.getName());
            return true;
        }

        if (!hasOthers) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.gamemode.others"));
            return true;
        }

        handleGamemode(player, args[0], args[1]);
        return true;
    }

    private void handleGamemode(CommandSender sender, String modeInput, String targetName) {
        GameMode mode = parseMode(modeInput);
        if (mode == null) {
            if (!(sender instanceof Player)) sender.sendMessage("Invalid gamemode.");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "errors.player-not-found", Map.of("player", targetName));
            } else {
                sender.sendMessage("Player not found.");
            }
            return;
        }

        plugin.scheduler().runEntityTask(target, () -> target.setGameMode(mode));

        String modeName = plugin.lang().get("utilities.gamemode.placeholders." + mode.name().toLowerCase());
        String senderName = (sender instanceof Player p) ? p.getName() : plugin.lang().get("general.console-name");

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

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender instanceof Player player && !player.hasPermission("aircore.command.gamemode")) {
                return Collections.emptyList();
            }
            return Stream.of("survival", "creative", "adventure", "spectator")
                    .filter(opt -> opt.startsWith(input))
                    .toList();
        }

        if (args.length == 2) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("aircore.command.gamemode.others")) return Collections.emptyList();
            }
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}