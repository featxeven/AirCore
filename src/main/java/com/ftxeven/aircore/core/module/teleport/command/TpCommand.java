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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TpCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.tp";
    private static final String PERM_OTHERS = "aircore.command.tp.others";

    public TpCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <player> <target>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            Player other = Bukkit.getPlayerExact(args[1]);

            if (target == null || other == null) {
                sender.sendMessage("One or both players not found.");
                return true;
            }

            String consoleName = (String) plugin.lang().get("general.console-name");
            Location destination = plugin.core().teleports().adjustToCenter(other.getLocation());
            plugin.core().teleports().teleport(target, destination);

            sender.sendMessage("Teleported " + target.getName() + " to " + other.getName() + ".");
            MessageUtil.send(target, "teleport.direct.to-player-by", Map.of("player", consoleName, "target", other.getName()));
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", getUsageString(player, label)));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", getUsageString(player, label)));
            return true;
        }

        boolean hasOthersPerm = player.hasPermission(PERM_OTHERS);
        if (args.length >= 2 && !hasOthersPerm) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_OTHERS));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        if (args.length == 1) {
            Location destination = plugin.core().teleports().adjustToCenter(target.getLocation());
            plugin.core().teleports().teleport(player, destination);
            MessageUtil.send(player, "teleport.direct.to-player", Map.of("player", target.getName()));
            return true;
        }

        Player other = Bukkit.getPlayerExact(args[1]);
        if (other == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[1]));
            return true;
        }

        handleDoubleTeleport(player, target, other);
        return true;
    }

    private void handleDoubleTeleport(Player player, Player target, Player other) {
        Location destination = plugin.core().teleports().adjustToCenter(other.getLocation());

        if (target.equals(player)) {
            plugin.core().teleports().teleport(player, destination);
            MessageUtil.send(player, "teleport.direct.to-player", Map.of("player", other.getName()));
            return;
        }

        if (other.equals(player)) {
            Location playerLoc = plugin.core().teleports().adjustToCenter(player.getLocation());
            plugin.core().teleports().teleport(target, playerLoc);
            MessageUtil.send(player, "teleport.direct.player-to-self", Map.of("player", target.getName()));
            return;
        }

        plugin.core().teleports().teleport(target, destination);
        MessageUtil.send(player, "teleport.direct.player-to-target", Map.of("player", target.getName(), "target", other.getName()));
        MessageUtil.send(target, "teleport.direct.to-player-by", Map.of("player", player.getName(), "target", other.getName()));
    }

    private String getUsageString(Player player, String label) {
        return player.hasPermission(PERM_OTHERS)
                ? plugin.commandConfig().getUsage("tp", "others", label)
                : plugin.commandConfig().getUsage("tp", label);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player player && !player.hasPermission(PERM_BASE)) return Collections.emptyList();
        if (args.length > 2) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();
        boolean canCompleteSecond = !(sender instanceof Player) || sender.hasPermission(PERM_OTHERS);

        if (args.length == 1 || canCompleteSecond) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}