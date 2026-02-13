package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.utility.UtilityManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DelWarpCommand implements TabExecutor {

    private final AirCore plugin;
    private final UtilityManager manager;

    public DelWarpCommand(AirCore plugin, UtilityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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

        if (!player.hasPermission("aircore.command.delwarp")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.delwarp"));
            return true;
        }

        if (args.length == 0) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("delwarp", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("delwarp", label)));
            return true;
        }

        String warpName = args[0].toLowerCase();

        if (!manager.warps().deleteWarp(warpName)) {
            MessageUtil.send(player, "utilities.warp.not-found", Map.of("name", warpName));
            return true;
        }

        MessageUtil.send(player, "utilities.warp.deleted", Map.of("name", warpName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission("aircore.command.delwarp")) return Collections.emptyList();

        var section = manager.warps().getConfig().getConfigurationSection("warps");
        if (section == null) return Collections.emptyList();

        String input = args[0].toLowerCase();
        return section.getKeys(false).stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}