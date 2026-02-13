package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.tppos"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("tppos", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 3) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("tppos", label)));
            return true;
        }

        try {
            final Location currentLoc = player.getLocation();
            final double x = parseCoordinate(args[0], currentLoc.getX());
            final double y = parseCoordinate(args[1], currentLoc.getY());
            final double z = parseCoordinate(args[2], currentLoc.getZ());

            final Location targetLoc = new Location(
                    player.getWorld(),
                    x, y, z,
                    currentLoc.getYaw(),
                    currentLoc.getPitch()
            );

            plugin.core().teleports().teleport(player, targetLoc);

            MessageUtil.send(player, "teleport.coords.success",
                    Map.of("x", Double.toString(Math.floor(x * 100) / 100),
                            "y", Double.toString(Math.floor(y * 100) / 100),
                            "z", Double.toString(Math.floor(z * 100) / 100)));

        } catch (NumberFormatException e) {
            MessageUtil.send(player, "teleport.coords.invalid", Map.of());
        }

        return true;
    }

    private double parseCoordinate(String arg, double relativeBase) throws NumberFormatException {
        if (arg.isEmpty()) throw new NumberFormatException();
        if (arg.charAt(0) == '~') {
            if (arg.length() == 1) return relativeBase;
            return relativeBase + Double.parseDouble(arg.substring(1));
        }
        return Double.parseDouble(arg);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length > 3) return Collections.emptyList();

        final Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock != null && !targetBlock.isEmpty()) {
            return switch (args.length) {
                case 1 -> List.of(Integer.toString(targetBlock.getX()));
                case 2 -> List.of(Integer.toString(targetBlock.getY()));
                case 3 -> List.of(Integer.toString(targetBlock.getZ()));
                default -> List.of("~");
            };
        }

        return List.of("~");
    }
}