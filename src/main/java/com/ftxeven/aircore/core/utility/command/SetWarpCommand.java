package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.utility.UtilityManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class SetWarpCommand implements TabExecutor {

    private final AirCore plugin;
    private final UtilityManager manager;

    public SetWarpCommand(AirCore plugin, UtilityManager manager) {
        this.manager = manager;
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

        if (!player.hasPermission("aircore.command.setwarp")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.setwarp"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("setwarp", label)));
            return true;
        }

        String warpName = args[0].toLowerCase();

        if (manager.warps().getConfig().contains("warps." + warpName)) {
            MessageUtil.send(player, "utilities.warp.already-exists", Map.of("name", warpName));
            return true;
        }

        manager.warps().saveWarp(warpName, player.getLocation());
        MessageUtil.send(player, "utilities.warp.created", Map.of("name", warpName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return List.of();
    }
}
