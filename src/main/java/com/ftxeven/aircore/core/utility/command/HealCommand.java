package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length < 1) {
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

        if (!player.hasPermission("aircore.command.heal")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.heal"));
            return true;
        }

        if (args.length == 0) {
            healPlayer(player);
            MessageUtil.send(player, "utilities.heal.self", Map.of());
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.heal.others");
        boolean hasAll = player.hasPermission("aircore.command.heal.all");
        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase("@a")) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.heal.all"));
                return true;
            }
        } else if (!targetArg.equalsIgnoreCase(player.getName())) {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.heal.others"));
                return true;
            }
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            String usage = (hasOthers || hasAll)
                    ? plugin.config().getUsage("heal", "others", label)
                    : plugin.config().getUsage("heal", label);

            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleHeal(player, targetArg);
        return true;
    }

    private void handleHeal(Player sender, String targetArg) {
        if (targetArg.equalsIgnoreCase("@a")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                healPlayer(target);
                if (!target.equals(sender)) {
                    MessageUtil.send(target, "utilities.heal.by", Map.of("player", sender.getName()));
                }
            }
            MessageUtil.send(sender, "utilities.heal.everyone", Map.of());
            return;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            MessageUtil.send(sender, "errors.player-not-found", Map.of("player", targetArg));
            return;
        }

        healPlayer(target);

        if (target.equals(sender)) {
            MessageUtil.send(sender, "utilities.heal.self", Map.of());
        } else {
            MessageUtil.send(sender, "utilities.heal.for", Map.of("player", target.getName()));
            MessageUtil.send(target, "utilities.heal.by", Map.of("player", sender.getName()));
        }
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

        if (!player.hasPermission("aircore.command.heal")) return Collections.emptyList();

        if (player.hasPermission("aircore.command.heal.others")) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(input)) suggestions.add(p.getName());
            });
        }

        if (player.hasPermission("aircore.command.heal.all") && "@a".startsWith(input)) {
            suggestions.add("@a");
        }

        return suggestions;
    }
}