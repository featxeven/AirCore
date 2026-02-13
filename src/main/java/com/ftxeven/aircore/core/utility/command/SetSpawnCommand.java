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

public final class SetSpawnCommand implements TabExecutor {

    private final AirCore plugin;
    private final UtilityManager manager;

    public SetSpawnCommand(AirCore plugin, UtilityManager manager) {
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

        if (!player.hasPermission("aircore.command.setspawn")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.setspawn"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("setspawn", label)));
            return true;
        }

        manager.spawn().saveSpawn(player.getLocation());
        MessageUtil.send(player, "utilities.spawn.set", Map.of());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}