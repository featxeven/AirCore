package com.ftxeven.aircore.module.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class TpPosCommand implements TabExecutor {

    private final AirCore plugin;

    public TpPosCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.tppos")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tppos"));
            return true;
        }

        if (args.length != 3) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("tppos", label)));
            return true;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "teleport.coords.invalid", Map.of());
            return true;
        }

        Location loc = new Location(
                player.getWorld(),
                x, y, z,
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );

        plugin.core().teleports().teleport(player, loc);

        MessageUtil.send(player, "teleport.coords.success",
                Map.of("x", String.valueOf(x),
                        "y", String.valueOf(y),
                        "z", String.valueOf(z)));

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
