package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class KillCommand implements TabExecutor {

    private final AirCore plugin;
    private final NamespacedKey deathReasonKey;

    public KillCommand(AirCore plugin) {
        this.plugin = plugin;
        this.deathReasonKey = new NamespacedKey(plugin, "death_reason");
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
                    killPlayer(target, consoleName);
                }
                sender.sendMessage("All players have been killed.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            killPlayer(target, consoleName);
            sender.sendMessage("Killed " + target.getName());
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.kill")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.kill"));
            return true;
        }

        // /kill self
        if (args.length == 0) {
            killPlayer(player, null);
            MessageUtil.send(player, "utilities.kill.self", Map.of());
            return true;
        }

        // /kill @a
        if (args[0].equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.kill.all")) {
                MessageUtil.send(player, "errors.player-not-found",
                        Map.of("player", "@a"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) {
                    killPlayerByPlayer(target, player);
                } else {
                    killPlayer(target, null);
                }
            }
            MessageUtil.send(player, "utilities.kill.everyone", Map.of());
            return true;
        }

        // /kill <player>
        if (!player.hasPermission("aircore.command.kill.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.kill.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found",
                    Map.of("player", args[0]));
            return true;
        }

        if (target.equals(player)) {
            killPlayer(player, null);
            MessageUtil.send(player, "utilities.kill.self", Map.of());
            return true;
        }

        killPlayerByPlayer(target, player);
        MessageUtil.send(player, "utilities.kill.other",
                Map.of("player", target.getName()));

        return true;
    }

    private void killPlayer(Player player, String killerName) {
        plugin.scheduler().runEntityTask(player, () -> {
            markForcedDeath(player);
            player.setHealth(0.0);
            if (killerName != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(player, "utilities.kill.by", Map.of("player", killerName));
            }
        });
    }

    private void killPlayerByPlayer(Player target, Player killer) {
        plugin.scheduler().runEntityTask(target, () -> {
            markForcedDeath(target);
            target.setHealth(0.0);
            MessageUtil.send(target, "utilities.kill.by",
                    Map.of("player", killer.getName()));
        });
    }

    private void markForcedDeath(Player player) {
        player.getPersistentDataContainer().set(deathReasonKey, PersistentDataType.STRING, "command");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.kill")) {
                return List.of();
            }
        }

        List<String> suggestions = List.of();

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.kill.others")) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (!(sender instanceof Player) || sender.hasPermission("aircore.command.kill.all")) {
            if ("@a".startsWith(input)) {
                suggestions = new ArrayList<>(suggestions);
                suggestions.add("@a");
            }
        }

        return suggestions;
    }
}