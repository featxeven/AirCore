package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.utility.UtilityManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class SpawnCommand implements TabExecutor {

    private final AirCore plugin;
    private final UtilityManager manager;

    public SpawnCommand(AirCore plugin, UtilityManager manager) {
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

        if (!player.hasPermission("aircore.command.spawn")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.spawn"));
            return true;
        }

        Location spawn = manager.spawn().loadSpawn();
        if (spawn == null) {
            MessageUtil.send(player, "utilities.spawn.none-set", Map.of());
            return true;
        }

        plugin.core().teleports().startCountdown(
                player,
                player,
                () -> {
                    plugin.core().teleports().teleport(player, spawn);
                    MessageUtil.send(player, "utilities.spawn.teleported", Map.of());
                },
                cancelReason -> MessageUtil.send(player, "utilities.spawn.cancelled", Map.of())
        );

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
