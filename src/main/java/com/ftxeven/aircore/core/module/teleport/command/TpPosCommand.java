package com.ftxeven.aircore.core.module.teleport.command;

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
    private static final String PERMISSION = "aircore.command.tppos";

    public TpPosCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("tppos", label);

        if (args.length < 3) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 3) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        try {
            Location current = player.getLocation();
            double x = parseCoordinate(args[0], current.getX());
            double y = parseCoordinate(args[1], current.getY());
            double z = parseCoordinate(args[2], current.getZ());

            Location target = new Location(player.getWorld(), x, y, z, current.getYaw(), current.getPitch());

            plugin.core().teleports().teleport(player, target);

            MessageUtil.send(player, "teleport.coords.success", Map.of(
                    "x", String.format("%.2f", x),
                    "y", String.format("%.2f", y),
                    "z", String.format("%.2f", z)
            ));

        } catch (NumberFormatException e) {
            MessageUtil.send(player, "teleport.coords.invalid", Map.of());
        }

        return true;
    }

    private double parseCoordinate(String arg, double relativeBase) throws NumberFormatException {
        if (arg.isEmpty()) throw new NumberFormatException();
        if (arg.startsWith("~")) {
            if (arg.length() == 1) return relativeBase;
            return relativeBase + Double.parseDouble(arg.substring(1));
        }
        return Double.parseDouble(arg);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION) || args.length > 3) {
            return Collections.emptyList();
        }

        Block target = player.getTargetBlockExact(5);
        String x = target != null ? String.valueOf(target.getX()) : "~";
        String y = target != null ? String.valueOf(target.getY()) : "~";
        String z = target != null ? String.valueOf(target.getZ()) : "~";

        return switch (args.length) {
            case 1 -> List.of(x + " " + y + " " + z, x + " " + y, x);
            case 2 -> List.of(y + " " + z, y);
            case 3 -> List.of(z);
            default -> Collections.emptyList();
        };
    }
}