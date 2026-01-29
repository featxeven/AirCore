package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WeatherCommand implements TabExecutor {

    private final AirCore plugin;

    public WeatherCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console execution
        if (!(sender instanceof Player player)) {
            if (args.length != 2) {
                sender.sendMessage("Usage: /" + label + " <clear|rain|thunder> <world>");
                return true;
            }

            String type = args[0].toLowerCase();
            if (!type.equals("clear") && !type.equals("rain") && !type.equals("thunder")) {
                sender.sendMessage("Invalid weather type. Use clear, rain, or thunder.");
                return true;
            }

            World targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                sender.sendMessage("World not found.");
                return true;
            }

            setWeather(targetWorld, type, () -> sender.sendMessage("Set weather in " + targetWorld.getName() + " to " + type));

            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.weather")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.weather"));
            return true;
        }

        if (args.length == 0) {
            if (player.hasPermission("aircore.command.weather.world")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("weather", "world", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("weather", label)));
            }
            return true;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("clear") && !type.equals("rain") && !type.equals("thunder")) {
            if (player.hasPermission("aircore.command.weather.world")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("weather", "world", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("weather", label)));
            }
            return true;
        }

        World targetWorld = player.getWorld();

        if (args.length == 2) {
            if (!player.hasPermission("aircore.command.weather.world")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.weather.world"));
                return true;
            }
            targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                MessageUtil.send(player, "errors.world-not-found",
                        Map.of("world", args[1]));
                return true;
            }
        } else if (args.length > 2) {
            MessageUtil.send(player, "errors.invalid-format", Map.of());
            return true;
        }

        World finalTargetWorld = targetWorld;
        setWeather(targetWorld, type, () -> {
            if (args.length == 2) {
                plugin.scheduler().runEntityTask(player, () ->
                        MessageUtil.send(player, "utilities.weather.set-in",
                                Map.of("type", plugin.lang().get("utilities.weather.placeholders." + type),
                                        "world", finalTargetWorld.getName())));
            } else {
                plugin.scheduler().runEntityTask(player, () ->
                        MessageUtil.send(player, "utilities.weather.set",
                                Map.of("type", plugin.lang().get("utilities.weather.placeholders." + type))));
            }
        });

        return true;
    }

    private void setWeather(World world, String type, Runnable post) {
        plugin.scheduler().runTask(() -> {
            switch (type) {
                case "clear" -> {
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "rain" -> {
                    world.setStorm(true);
                    world.setThundering(false);
                }
                case "thunder" -> {
                    world.setStorm(true);
                    world.setThundering(true);
                }
            }
            if (post != null) post.run();
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.weather")) {
                return List.of();
            }
        }

        if (args.length == 1) {
            return Stream.of("clear", "rain", "thunder")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String type = args[0].toLowerCase();
            if (type.equals("clear") || type.equals("rain") || type.equals("thunder")) {
                if (!(sender instanceof Player) || sender.hasPermission("aircore.command.weather.world")) {
                    return Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .limit(20)
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }
}
