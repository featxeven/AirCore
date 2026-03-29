package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
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
    private static final String PERMISSION = "aircore.command.setspawn";

    public SetSpawnCommand(AirCore plugin) {
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

        if (args.length > 0) {
            sendError(player, label);
            return true;
        }

        plugin.utility().spawn().saveSpawn(player.getLocation());
        MessageUtil.send(player, "utilities.spawn.set", Map.of());
        return true;
    }

    private void sendError(Player player, String label) {
        String usage = plugin.commandConfig().getUsage("setspawn", null, label);
        MessageUtil.send(player, "errors." + "too-many-arguments", Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}