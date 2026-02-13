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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length < 1) {
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
                sender.sendMessage("Player not found in database.");
                return true;
            }

            killPlayer(target, consoleName);
            sender.sendMessage("Killed " + target.getName());
            return true;
        }

        if (!player.hasPermission("aircore.command.kill")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.kill"));
            return true;
        }

        if (args.length == 0) {
            killPlayer(player, null);
            MessageUtil.send(player, "utilities.kill.self", Map.of());
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.kill.others");
        boolean hasAll = player.hasPermission("aircore.command.kill.all");
        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase("@a")) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.kill.all"));
                return true;
            }
        } else if (!targetArg.equalsIgnoreCase(player.getName())) {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.kill.others"));
                return true;
            }
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            String usage = (hasOthers || hasAll)
                    ? plugin.config().getUsage("kill", "others", label)
                    : plugin.config().getUsage("kill", label);

            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleKill(player, targetArg);
        return true;
    }

    private void handleKill(Player sender, String targetArg) {
        if (targetArg.equalsIgnoreCase("@a")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(sender)) {
                    killPlayerByPlayer(target, sender);
                } else {
                    killPlayer(target, null);
                }
            }
            MessageUtil.send(sender, "utilities.kill.everyone", Map.of());
            return;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            MessageUtil.send(sender, "errors.player-not-found", Map.of("player", targetArg));
            return;
        }

        if (target.equals(sender)) {
            killPlayer(sender, null);
            MessageUtil.send(sender, "utilities.kill.self", Map.of());
            return;
        }

        killPlayerByPlayer(target, sender);
        MessageUtil.send(sender, "utilities.kill.other", Map.of("player", target.getName()));
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
            MessageUtil.send(target, "utilities.kill.by", Map.of("player", killer.getName()));
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

        if (args.length != 1) return Collections.emptyList();

        String input = args[0].toLowerCase();
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player player)) {
            if ("@a".startsWith(input)) suggestions.add("@a");
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .forEach(suggestions::add);
            return suggestions;
        }

        if (!player.hasPermission("aircore.command.kill")) return Collections.emptyList();

        if (player.hasPermission("aircore.command.kill.others")) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(input)) suggestions.add(p.getName());
            });
        }

        if (player.hasPermission("aircore.command.kill.all") && "@a".startsWith(input)) {
            suggestions.add("@a");
        }

        return suggestions;
    }
}