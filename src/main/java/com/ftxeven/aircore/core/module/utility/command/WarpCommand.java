package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class WarpCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.warp";
    public WarpCommand(AirCore plugin) {
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

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length == 0) {
            sendError(player, label, "incorrect-usage");
            return true;
        }

        if (args.length > 1) {
            sendError(player, label, "too-many-arguments");
            return true;
        }

        String warpName = args[0].toLowerCase();
        Location loc = plugin.utility().warps().loadWarp(warpName);

        if (loc == null) {
            MessageUtil.send(player, "utilities.warp.not-found", Map.of());
            return true;
        }

        String subPerm = PERMISSION + "." + warpName;
        if (!player.hasPermission(PERMISSION + ".*") && !player.hasPermission(subPerm)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", subPerm));
            return true;
        }

        plugin.core().teleports().startCountdown(
                player,
                player,
                () -> {
                    plugin.core().teleports().teleport(player, loc);
                    MessageUtil.send(player, "utilities.warp.teleported", Map.of("name", warpName));
                },
                cancelReason -> MessageUtil.send(player, "utilities.warp.cancelled", Map.of("name", warpName))
        );
        return true;
    }

    private void sendError(Player player, String label, String key) {
        String usage = plugin.commandConfig().getUsage("warp", null, label);
        MessageUtil.send(player, "errors." + key, Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            var section = plugin.utility().warps().getConfig().getConfigurationSection("warps");
            if (section == null) return Collections.emptyList();

            String input = args[0].toLowerCase();
            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .filter(name -> player.hasPermission(PERMISSION + ".*") ||
                            player.hasPermission(PERMISSION + "." + name.toLowerCase()))
                    .limit(20)
                    .toList();
        }
        return Collections.emptyList();
    }
}