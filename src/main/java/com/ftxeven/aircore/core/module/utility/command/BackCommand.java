package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BackCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.back";

    public BackCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player> [-countdown]");
                return true;
            }
            handleBack(sender, args[0], args.length > 1 && args[1].equalsIgnoreCase("-countdown"));
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length > 0) {
            String usage = plugin.commandConfig().getUsage("back", null, label);
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleBack(player, player.getName(), true);
        return true;
    }

    private void handleBack(CommandSender sender, String targetName, boolean useCountdown) {
        Player target = Bukkit.getPlayerExact(targetName);
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (target == null) {
            if (!(sender instanceof Player)) sender.sendMessage("Player not found.");
            return;
        }

        Location deathLoc = plugin.utility().back().getLastDeath(target.getUniqueId());
        if (deathLoc == null) {
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.back.no-location", Map.of());
            else sender.sendMessage(target.getName() + " has no previous death location.");
            return;
        }

        if (useCountdown) {
            plugin.core().teleports().startCountdown(target, target, () -> {
                executeTeleport(sender, target, deathLoc, senderName);
            }, cancelReason -> MessageUtil.send(target, "utilities.back.cancelled", Map.of()));

            if (!(sender instanceof Player)) {
                sender.sendMessage("Countdown started for " + target.getName());
            }
        } else {
            executeTeleport(sender, target, deathLoc, senderName);
        }
    }

    private void executeTeleport(CommandSender sender, Player target, Location loc, String senderName) {
        plugin.core().teleports().teleport(target, loc);
        plugin.utility().back().clearLastDeath(target.getUniqueId());

        if (sender instanceof Player p) {
            MessageUtil.send(p, "utilities.back.success", Map.of());
        } else {
            sender.sendMessage("Teleported " + target.getName() + " to death location.");
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.back.success-by", Map.of("player", senderName));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (sender instanceof Player) return Collections.emptyList();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        if (args.length == 2 && "-countdown".startsWith(args[1].toLowerCase())) {
            return List.of("-countdown");
        }

        return Collections.emptyList();
    }
}