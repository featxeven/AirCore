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

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player|@a>");
                return true;
            }
            handleFeed(sender, args[0]);
            return true;
        }

        if (!player.hasPermission("aircore.command.feed")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.feed"));
            return true;
        }

        if (args.length == 0) {
            handleFeed(player, player.getName());
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.feed.others");
        boolean hasAll = player.hasPermission("aircore.command.feed.all");
        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase("@a")) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.feed.all"));
                return true;
            }
        } else if (!targetArg.equalsIgnoreCase(player.getName())) {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.feed.others"));
                return true;
            }
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            String usage = (hasOthers || hasAll)
                    ? plugin.config().getUsage("feed", "others", label)
                    : plugin.config().getUsage("feed", label);

            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleFeed(player, targetArg);
        return true;
    }

    private void handleFeed(CommandSender sender, String targetName) {
        String consoleName = plugin.lang().get("general.console-name");
        String senderName = (sender instanceof Player p) ? p.getName() : consoleName;

        if (targetName.equalsIgnoreCase("@a")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                performFeed(target);
                if (!target.equals(sender)) {
                    if (sender instanceof Player || plugin.config().consoleToPlayerFeedback()) {
                        MessageUtil.send(target, "utilities.feed.by", Map.of("player", senderName));
                    }
                }
            }

            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.feed.everyone", Map.of());
            } else {
                sender.sendMessage("All players have been fed.");
            }
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

        performFeed(target);

        if (sender instanceof Player p) {
            if (target.equals(p)) {
                MessageUtil.send(p, "utilities.feed.self", Map.of());
            } else {
                MessageUtil.send(p, "utilities.feed.for", Map.of("player", target.getName()));
                MessageUtil.send(target, "utilities.feed.by", Map.of("player", p.getName()));
            }
        } else {
            sender.sendMessage("Fed " + target.getName());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.feed.by", Map.of("player", consoleName));
            }
        }
    }

    private void performFeed(Player target) {
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

        if (!player.hasPermission("aircore.command.feed")) return Collections.emptyList();

        if (player.hasPermission("aircore.command.feed.others")) {
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .forEach(suggestions::add);
        }

        if (player.hasPermission("aircore.command.feed.all") && "@a".startsWith(input)) {
            if (!suggestions.contains("@a")) suggestions.add("@a");
        }

        return suggestions;
    }
}