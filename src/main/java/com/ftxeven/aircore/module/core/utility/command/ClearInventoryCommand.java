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

public final class ClearInventoryCommand implements TabExecutor {

    private final AirCore plugin;

    public ClearInventoryCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.clearinventory")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.clearinventory"));
            return true;
        }

        // /clearinventory (no args)
        if (args.length == 0) {
            clearInventory(player);
            MessageUtil.send(player, "utilities.inventory.cleared", Map.of());
            return true;
        }

        // /clearinventory @a
        if (args[0].equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.clearinventory.all")) {
                MessageUtil.send(player, "errors.player-not-found",
                        Map.of("player", "@a"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                clearInventory(target);
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.inventory.cleared-by",
                            Map.of("player", player.getName()));
                }
            }
            MessageUtil.send(player, "utilities.inventory.cleared-everyone", Map.of());
            return true;
        }

        // /clearinventory <player>
        if (!player.hasPermission("aircore.command.clearinventory.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.clearinventory.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found",
                    Map.of("player", args[0]));
            return true;
        }

        if (target.equals(player)) {
            clearInventory(player);
            MessageUtil.send(player, "utilities.inventory.cleared", Map.of());
            return true;
        }

        clearInventory(target);
        MessageUtil.send(player, "utilities.inventory.cleared-for",
                Map.of("player", target.getName()));
        MessageUtil.send(target, "utilities.inventory.cleared-by",
                Map.of("player", player.getName()));

        return true;
    }

    private void clearInventory(Player target) {
        plugin.scheduler().runEntityTask(target, () ->
                target.getInventory().clear()
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.clearinventory")) {
                return List.of();
            }
        }

        List<String> suggestions = List.of();

        if (sender.hasPermission("aircore.command.clearinventory.others")) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (sender.hasPermission("aircore.command.clearinventory.all")) {
            if ("@a".startsWith(input)) {
                suggestions = new java.util.ArrayList<>(suggestions);
                suggestions.add("@a");
            }
        }

        return suggestions;
    }
}