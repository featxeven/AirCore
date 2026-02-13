package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.utility.UtilityManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
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
        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("aircore.command.delwarp")) return List.of();

        if (args.length == 1) {
            var section = manager.warps().getConfig().getConfigurationSection("warps");
            if (section == null) return List.of();

            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(20)
                    .toList();
        }
        return List.of();
    }
}
