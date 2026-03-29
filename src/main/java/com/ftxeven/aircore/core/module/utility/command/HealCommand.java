package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
    private static final String PERM_BASE = "aircore.command.heal";
    private static final String PERM_OTHERS = "aircore.command.heal.others";
    private static final String PERM_ALL = "aircore.command.heal.all";

    public HealCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleHeal(sender, args[0], selectorAll);
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);
        boolean hasAll = player.hasPermission(PERM_ALL);
        boolean hasExtended = hasOthers || hasAll;

        if (args.length == 0) {
            handleHeal(player, player.getName(), selectorAll);
            return true;
        }

        if (!hasExtended || args.length > 1) {
            sendError(player, label, hasExtended);
            return true;
        }

        String targetArg = args[0];
        if (targetArg.equalsIgnoreCase(selectorAll)) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
        } else {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_OTHERS));
                return true;
            }
        }

        handleHeal(player, targetArg, selectorAll);
        return true;
    }

    private void handleHeal(CommandSender sender, String targetArg, String selectorAll) {
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));
        boolean feedbackEnabled = plugin.config().consoleToPlayerFeedback();

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                performHeal(target);
                if (!target.equals(sender)) {
                    MessageUtil.send(target, "utilities.heal.by", Map.of("player", senderName));
                }
            }
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.heal.everyone", Map.of());
            else sender.sendMessage("All players healed.");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            if (sender instanceof Player p) MessageUtil.send(p, "errors.player-not-found", Map.of("player", targetArg));
            else sender.sendMessage("Player not found");
            return;
        }

        performHeal(target);
        if (sender instanceof Player p) {
            if (target.equals(p)) MessageUtil.send(p, "utilities.heal.self", Map.of());
            else {
                MessageUtil.send(p, "utilities.heal.for", Map.of("player", target.getName()));
                MessageUtil.send(target, "utilities.heal.by", Map.of("player", p.getName()));
            }
        } else {
            sender.sendMessage("Healed " + target.getName());
            if (feedbackEnabled) {
                MessageUtil.send(target, "utilities.heal.by", Map.of("player", senderName));
            }
        }
    }

    private void performHeal(Player player) {
        plugin.scheduler().runEntityTask(player, () -> {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
        });
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("heal", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        if (sender instanceof Player player) {
            if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

            if (player.hasPermission(PERM_OTHERS)) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(input))
                        .limit(20)
                        .forEach(suggestions::add);
            }
            if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
                suggestions.add(selectorAll);
            }
        } else {
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .limit(20)
                    .forEach(suggestions::add);

            if (selectorAll.toLowerCase().startsWith(input)) {
                suggestions.add(selectorAll);
            }
        }

        return suggestions;
    }
}