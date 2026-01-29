package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class SpeedCommand implements TabExecutor {

    private static final double MIN_SPEED = 0.0;
    private static final double MAX_SPEED = 10.0;

    private final AirCore plugin;

    public SpeedCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    private float toApiSpeed(String type, double value) {
        double scaled = type.equals("flying") ? (value * 0.1) : (value * 0.2);
        if (scaled < 0.0) scaled = 0.0;
        if (scaled > 1.0) scaled = 1.0;
        return (float) scaled;
    }

    private String formatSpeed(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    private String formatType(String type) {
        return type.equals("flying") ? plugin.lang().get("utilities.speed.placeholders.flying") : plugin.lang().get("utilities.speed.placeholders.walking");
    }

    private void applySpeed(Player target, String type, double value) {
        float apiValue = toApiSpeed(type, value);
        plugin.scheduler().runEntityTask(target, () -> {
            if (type.equals("flying")) {
                target.setFlySpeed(apiValue);
            } else {
                target.setWalkSpeed(apiValue);
            }
        });
        plugin.scheduler().runAsync(() ->
                plugin.database().records().setSpeed(target.getUniqueId(), value)
        );
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console behavior
        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " [walking|flying] <speed> <player...>");
                return true;
            }

            String type = null;
            int index = 0;
            if (args[0].equalsIgnoreCase("walking") || args[0].equalsIgnoreCase("flying")) {
                type = args[0].toLowerCase();
                index++;
            }

            double value;
            try {
                value = Double.parseDouble(args[index]);
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid speed value.");
                return true;
            }

            if (value < MIN_SPEED || value > MAX_SPEED) {
                sender.sendMessage("Speed value must be between " + (int) MIN_SPEED + " and " + (int) MAX_SPEED + ".");
                return true;
            }

            if (args.length <= index + 1) {
                sender.sendMessage("Usage: /" + label + " [walking|flying] <speed> <player>");
                return true;
            }

            for (int i = index + 1; i < args.length; i++) {
                Player target = Bukkit.getPlayerExact(args[i]);
                if (target == null) {
                    sender.sendMessage("Player not found: " + args[i]);
                    continue;
                }

                if (type == null) {
                    type = target.isFlying() ? "flying" : "walking";
                }

                applySpeed(target, type, value);

                sender.sendMessage("Set " + formatType(type) + " speed for " + target.getName() + " -> " + formatSpeed(value));

                if (plugin.config().consoleToPlayerFeedback()) {
                    MessageUtil.send(target, "utilities.speed.set-by",
                            Map.of("player", consoleName,
                                    "type", formatType(type),
                                    "speed", formatSpeed(value)));
                }
            }
            return true;
        }

        // Player behavior
        if (!player.hasPermission("aircore.command.speed")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.speed"));
            return true;
        }

        if (args.length < 1) {
            if (player.hasPermission("aircore.command.speed.others")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("speed", "others", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("speed", label)));
            }
            return true;
        }

        String type = null;
        int index = 0;
        if (args[0].equalsIgnoreCase("walking") || args[0].equalsIgnoreCase("flying")) {
            type = args[0].toLowerCase();
            index++;
        }

        if (args.length <= index) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("speed", label)));
            return true;
        }

        double value;
        try {
            value = Double.parseDouble(args[index]);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "errors.invalid-format", Map.of());
            return true;
        }

        if (value < MIN_SPEED || value > MAX_SPEED) {
            MessageUtil.send(player, "utilities.speed.limit",
                    Map.of("min", String.valueOf(0), "max", String.valueOf((int) MAX_SPEED)));
            return true;
        }

        if (player.hasPermission("aircore.command.speed.others") && args.length > index + 1) {
            for (int i = index + 1; i < args.length; i++) {
                Player target = Bukkit.getPlayerExact(args[i]);
                if (target == null) {
                    MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[i]));
                    continue;
                }

                if (type == null) {
                    type = target.isFlying() ? "flying" : "walking";
                }

                applySpeed(target, type, value);

                if (target.equals(player)) {
                    // Treat as self
                    MessageUtil.send(player, "utilities.speed.set",
                            Map.of("type", formatType(type), "speed", formatSpeed(value)));
                } else {
                    // Others branch
                    MessageUtil.send(player, "utilities.speed.set-for",
                            Map.of("type", formatType(type),
                                    "speed", formatSpeed(value),
                                    "player", target.getName()));
                    MessageUtil.send(target, "utilities.speed.set-by",
                            Map.of("player", player.getName(),
                                    "type", formatType(type),
                                    "speed", formatSpeed(value)));
                }
            }
            return true;
        }

        // Self only
        if (type == null) {
            type = player.isFlying() ? "flying" : "walking";
        }

        applySpeed(player, type, value);
        MessageUtil.send(player, "utilities.speed.set",
                Map.of("type", formatType(type), "speed", formatSpeed(value)));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("aircore.command.speed")) {
                    return List.of();
                }
            }
            return List.of("walking", "flying");
        }

        if (args.length >= 2) {
            if (!(sender instanceof Player) || sender.hasPermission("aircore.command.speed.others")) {
                String input = args[args.length - 1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .toList();
            }
        }

        return List.of();
    }
}