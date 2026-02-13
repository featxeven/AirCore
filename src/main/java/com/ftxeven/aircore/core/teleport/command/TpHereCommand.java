package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TpHereCommand implements TabExecutor {

    private final AirCore plugin;

    public TpHereCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.tphere")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tphere"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("tphere", label)));
            return true;
        }

        String targetName = args[0];

        // Handle @a (teleport everyone)
        if (targetName.equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.tphere.all")) {
                MessageUtil.send(player, "errors.player-not-found", Map.of("player", "@a"));
                return true;
            }

            List<Player> others = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .collect(Collectors.toList());

            if (others.isEmpty()) {
                MessageUtil.send(player, "errors.no-players-online", Map.of());
                return true;
            }

            // Teleport everyone to executor
            for (Player other : others) {
                plugin.core().teleports().teleport(other, player.getLocation());
            }

            MessageUtil.send(player, "teleport.direct.everyone-to-self", Map.of());
            return true;
        }

        // Normal single-target teleport
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", targetName));
            return true;
        }

        plugin.core().teleports().teleport(target, player.getLocation());

        MessageUtil.send(player, "teleport.direct.player-to-self",
                Map.of("player", target.getName()));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String[] args) {

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();

        if (sender.hasPermission("aircore.command.tphere.all") && "@a".startsWith(input)) {
            completions.add("@a");
        }

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .forEach(completions::add);

        return completions;
    }
}
