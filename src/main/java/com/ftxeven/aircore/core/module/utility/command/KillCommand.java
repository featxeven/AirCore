package com.ftxeven.aircore.core.module.utility.command;

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
    private static final String PERM_BASE = "aircore.command.kill";
    private static final String PERM_OTHERS = "aircore.command.kill.others";
    private static final String PERM_ALL = "aircore.command.kill.all";

    public KillCommand(AirCore plugin) {
        this.plugin = plugin;
        this.deathReasonKey = new NamespacedKey(plugin, "death_reason");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleKill(sender, args[0], selectorAll);
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
            handleKill(player, player.getName(), selectorAll);
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

        handleKill(player, targetArg, selectorAll);
        return true;
    }

    private void handleKill(CommandSender sender, String targetArg, String selectorAll) {
        String consoleName = String.valueOf(plugin.lang().get("general.console-name"));
        String senderName = (sender instanceof Player p) ? p.getName() : consoleName;
        boolean feedbackEnabled = plugin.config().consoleToPlayerFeedback();

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                performKillAction(target);
                if (!target.equals(sender)) {
                    MessageUtil.send(target, "utilities.kill.by", Map.of("player", senderName));
                }
            }
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.kill.everyone", Map.of());
            else sender.sendMessage("All players have been killed.");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            if (sender instanceof Player p) MessageUtil.send(p, "errors.player-not-found", Map.of("player", targetArg));
            else sender.sendMessage("Player not found");
            return;
        }

        performKillAction(target);

        if (sender instanceof Player p) {
            if (target.equals(p)) {
                MessageUtil.send(p, "utilities.kill.self", Map.of());
            } else {
                MessageUtil.send(p, "utilities.kill.other", Map.of("player", target.getName()));
                MessageUtil.send(target, "utilities.kill.by", Map.of("player", p.getName()));
            }
        } else {
            sender.sendMessage("Killed " + target.getName());
            if (feedbackEnabled) {
                MessageUtil.send(target, "utilities.kill.by", Map.of("player", senderName));
            }
        }
    }

    private void performKillAction(Player target) {
        plugin.scheduler().runEntityTask(target, () -> {
            target.getPersistentDataContainer().set(deathReasonKey, PersistentDataType.STRING, "command");
            target.setHealth(0.0);
        });
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("kill", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        if (sender instanceof Player player) {
            if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

            if (player.hasPermission(PERM_OTHERS)) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .forEach(suggestions::add);
            }

            if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
                suggestions.add(selectorAll);
            }
        } else {
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .forEach(suggestions::add);

            if (selectorAll.toLowerCase().startsWith(input)) {
                suggestions.add(selectorAll);
            }
        }

        return suggestions;
    }
}