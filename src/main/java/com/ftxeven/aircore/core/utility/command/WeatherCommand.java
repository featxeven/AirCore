package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <clear|rain|thunder> <world>");
                return true;
            }

            String type = args[0].toLowerCase();
            World targetWorld = Bukkit.getWorld(args[1]);

            if (!isValidWeather(type)) {
                sender.sendMessage("Invalid weather type. Use clear, rain, or thunder.");
                return true;
            }

            if (targetWorld == null) {
                sender.sendMessage("World not found.");
                return true;
            }

            setWeather(targetWorld, type, () -> sender.sendMessage("Set weather in " + targetWorld.getName() + " to " + type));
            return true;
        }

        if (!player.hasPermission("aircore.command.weather")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.weather"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        boolean hasWorldPerm = player.hasPermission("aircore.command.weather.world");
        String type = args[0].toLowerCase();

        if (args.length >= 2 && !hasWorldPerm) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.weather.world"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs()) {
            int limit = hasWorldPerm ? 2 : 1;
            if (args.length > limit) {
                sendTooManyArgs(player, label, hasWorldPerm);
                return true;
            }
        }

        if (!isValidWeather(type)) {
            sendUsage(player, label);
            return true;
        }

        World targetWorld = player.getWorld();
        if (args.length == 2) {
            targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                MessageUtil.send(player, "errors.world-not-found", Map.of("world", args[1]));
                return true;
            }
        }

        final World finalWorld = targetWorld;
        setWeather(finalWorld, type, () -> plugin.scheduler().runEntityTask(player, () -> {
            String typeDisplay = plugin.lang().get("utilities.weather.placeholders." + type);
            if (args.length == 2) {
                MessageUtil.send(player, "utilities.weather.set-in", Map.of("type", typeDisplay, "world", finalWorld.getName()));
            } else {
                MessageUtil.send(player, "utilities.weather.set", Map.of("type", typeDisplay));
            }
        }));

        return true;
    }

    private boolean isValidWeather(String type) {
        return type.equals("clear") || type.equals("rain") || type.equals("thunder");
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

    private void sendUsage(Player player, String label) {
        String usage;
        if (player.hasPermission("aircore.command.weather.world")) {
            usage = plugin.config().getUsage("weather", "world", label);
        } else {
            usage = plugin.config().getUsage("weather", label);
        }
        MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
    }

    private void sendTooManyArgs(Player player, String label, boolean hasWorldPerm) {
        String usage;
        if (hasWorldPerm) {
            usage = plugin.config().getUsage("weather", "world", label);
        } else {
            usage = plugin.config().getUsage("weather", label);
        }
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        String input = args[args.length - 1].toLowerCase();

        if (sender instanceof Player player && !player.hasPermission("aircore.command.weather")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Stream.of("clear", "rain", "thunder").filter(opt -> opt.startsWith(input)).toList();
        }

        if (args.length == 2) {
            if (!(sender instanceof Player player) || player.hasPermission("aircore.command.weather.world")) {
                return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}