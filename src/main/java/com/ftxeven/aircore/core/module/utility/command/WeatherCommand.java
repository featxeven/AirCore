package com.ftxeven.aircore.core.module.utility.command;

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
    private static final String PERMISSION = "aircore.command.weather";

    public WeatherCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length > 2) {
            sendError(sender, label, "too-many-arguments");
            return true;
        }

        if (args.length == 0) {
            sendError(sender, label, "incorrect-usage");
            return true;
        }

        String thunderSel = plugin.commandConfig().getSelector("weather", "thunder");
        String rainSel = plugin.commandConfig().getSelector("weather", "rain");
        String clearSel = plugin.commandConfig().getSelector("weather", "clear");

        String inputType = args[0].toLowerCase();
        String type;

        if (inputType.equals(thunderSel)) type = "thunder";
        else if (inputType.equals(rainSel)) type = "rain";
        else if (inputType.equals(clearSel)) type = "clear";
        else {
            sendError(sender, label, "incorrect-usage");
            return true;
        }

        World targetWorld;
        if (args.length == 2) {
            targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.world-not-found", Map.of());
                else sender.sendMessage("World not found");
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /" + label + " <type> <world>");
                return true;
            }
            targetWorld = player.getWorld();
        }

        setWeather(targetWorld, type);

        String typeDisplay = String.valueOf(plugin.lang().get("utilities.weather.placeholders." + type));
        if (sender instanceof Player p) {
            if (args.length == 2) {
                MessageUtil.send(p, "utilities.weather.set-in", Map.of("type", typeDisplay, "world", targetWorld.getName()));
            } else {
                MessageUtil.send(p, "utilities.weather.set", Map.of("type", typeDisplay));
            }
        } else {
            sender.sendMessage("Set weather in " + targetWorld.getName() + " to " + type);
        }

        return true;
    }

    private void setWeather(World world, String type) {
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
        });
    }

    private void sendError(CommandSender sender, String label, String key) {
        String usage = plugin.commandConfig().getUsage("weather", null, label);
        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            sender.sendMessage("Usage: /" + label + " <type> <world>");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return Stream.of(
                            plugin.commandConfig().getSelector("weather", "clear"),
                            plugin.commandConfig().getSelector("weather", "rain"),
                            plugin.commandConfig().getSelector("weather", "thunder")
                    )
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        if (args.length == 2) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}