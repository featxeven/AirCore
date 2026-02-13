package com.ftxeven.aircore.core.utility.command;

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

    public BackCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            handleConsole(sender, label, args);
            return true;
        }

        if (!player.hasPermission("aircore.command.back")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.back"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("back", label)));
            return true;
        }

        return teleportSelf(player);
    }

    private void handleConsole(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <player> [-countdown]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return;
        }

        Location deathLoc = plugin.utility().back().getLastDeath(target.getUniqueId());
        if (deathLoc == null) {
            sender.sendMessage(target.getName() + " has no previous death location.");
            return;
        }

        final String consoleName = plugin.lang().get("general.console-name");
        boolean useCountdown = args.length > 1 && args[1].equalsIgnoreCase("-countdown");

        if (useCountdown) {
            plugin.core().teleports().startCountdown(
                    target,
                    target,
                    () -> {
                        plugin.core().teleports().teleport(target, deathLoc);
                        plugin.utility().back().clearLastDeath(target.getUniqueId());
                        MessageUtil.send(target, "utilities.back.success-by", Map.of("player", consoleName));
                    },
                    cancelReason -> MessageUtil.send(target, "utilities.back.cancelled", Map.of())
            );
            sender.sendMessage("Countdown started for " + target.getName() + " to teleport to last death location.");
        } else {
            plugin.core().teleports().teleport(target, deathLoc);
            plugin.utility().back().clearLastDeath(target.getUniqueId());

            sender.sendMessage("Teleported " + target.getName() + " to their last death location.");
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.back.success-by", Map.of("player", consoleName));
            }
        }
    }

    private boolean teleportSelf(Player player) {
        Location deathLoc = plugin.utility().back().getLastDeath(player.getUniqueId());
        if (deathLoc == null) {
            MessageUtil.send(player, "utilities.back.no-location", Map.of());
            return true;
        }

        plugin.core().teleports().startCountdown(
                player,
                player,
                () -> {
                    plugin.core().teleports().teleport(player, deathLoc);
                    plugin.utility().back().clearLastDeath(player.getUniqueId());
                    MessageUtil.send(player, "utilities.back.success", Map.of());
                },
                cancelReason -> MessageUtil.send(player, "utilities.back.cancelled", Map.of())
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (sender instanceof Player) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        if (args.length == 2 && "-countdown".startsWith(args[1].toLowerCase())) {
            return List.of("-countdown");
        }

        return Collections.emptyList();
    }
}