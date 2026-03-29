package com.ftxeven.aircore.core.module.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TpHereCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.tphere";
    private static final String PERM_ALL = "aircore.command.tphere.all";

    public TpHereCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("tphere", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            if (!player.hasPermission(PERM_ALL)) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
            handleTeleportAll(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", targetArg));
            return true;
        }

        plugin.core().teleports().teleport(target, player.getLocation());
        MessageUtil.send(player, "teleport.direct.player-to-self", Map.of("player", target.getName()));
        return true;
    }

    private void handleTeleportAll(Player player) {
        final Location destination = plugin.core().teleports().adjustToCenter(player.getLocation());

        List<Player> others = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .map(p -> (Player) p)
                .toList();

        if (others.isEmpty()) {
            MessageUtil.send(player, "errors.no-players-online", Map.of());
            return;
        }

        for (Player other : others) {
            plugin.core().teleports().teleport(other, destination);
        }

        MessageUtil.send(player, "teleport.direct.everyone-to-self", Map.of());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .limit(20)
                .forEach(suggestions::add);

        if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
            suggestions.add(selectorAll);
        }

        return suggestions;
    }
}