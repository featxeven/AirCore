package com.ftxeven.aircore.module.core.utility.command;

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
import java.util.stream.Collectors;

public final class HealCommand implements TabExecutor {

    private final AirCore plugin;

    public HealCommand(AirCore plugin) {
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
                    healPlayer(target);
                    if (plugin.config().consoleToPlayerFeedback()) {
                        MessageUtil.send(target, "utilities.heal.by", Map.of("player", consoleName));
                    }
                }
                sender.sendMessage("All players have been healed.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            healPlayer(target);
            sender.sendMessage("Healed " + target.getName());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.heal.by", Map.of("player", consoleName));
            }
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.heal")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.heal"));
            return true;
        }

        // /heal (self)
        if (args.length == 0) {
            healPlayer(player);
            MessageUtil.send(player, "utilities.heal.self", Map.of());
            return true;
        }

        // /heal @a
        if (args[0].equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.heal.all")) {
                MessageUtil.send(player, "errors.player-not-found", Map.of("player", "@a"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                healPlayer(target);
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.heal.by", Map.of("player", player.getName()));
                }
            }
            MessageUtil.send(player, "utilities.heal.everyone", Map.of());
            return true;
        }

        // /heal <player>
        if (!player.hasPermission("aircore.command.heal.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.heal.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        if (target.equals(player)) {
            healPlayer(player);
            MessageUtil.send(player, "utilities.heal.self", Map.of());
            return true;
        }

        healPlayer(target);
        MessageUtil.send(player, "utilities.heal.for", Map.of("player", target.getName()));
        MessageUtil.send(target, "utilities.heal.by", Map.of("player", player.getName()));

        return true;
    }

    private void healPlayer(Player player) {
        plugin.scheduler().runEntityTask(player, () -> {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
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
            if (!player.hasPermission("aircore.command.heal")) {
                return List.of();
            }
        }

        List<String> suggestions = List.of();

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.heal.others")) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.heal.all")) {
            if ("@a".startsWith(input)) {
                suggestions = new java.util.ArrayList<>(suggestions);
                suggestions.add("@a");
            }
        }

        return suggestions;
    }
}