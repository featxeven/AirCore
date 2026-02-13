package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TimeCommand implements TabExecutor {

    private final AirCore plugin;

    public TimeCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console execution
        if (!(sender instanceof Player player)) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /" + label + " <set|add> <time> <world>");
                return true;
            }

            String type = args[0].toLowerCase();
            if (!type.equals("set") && !type.equals("add")) {
                sender.sendMessage("Usage: /" + label + " <set|add> <time> <world>");
                return true;
            }

            long ticks;
            switch (args[1].toLowerCase()) {
                case "day" -> ticks = 1000;
                case "noon" -> ticks = 6000;
                case "night" -> ticks = 13000;
                case "midnight" -> ticks = 18000;
                default -> {
                    Long parsed = TimeUtil.parseDurationTicks(args[1]);
                    if (parsed == null) {
                        sender.sendMessage("Invalid format");
                        return true;
                    }
                    ticks = parsed;
                }
            }

            World world = Bukkit.getWorld(args[2]);
            if (world == null) {
                sender.sendMessage("World not found");
                return true;
            }

            if (type.equals("set")) {
                setWorldTime(world, ticks, () ->
                        sender.sendMessage("Set time in " + world.getName() + " to " + formatTime(ticks) + " (" + ticks + " ticks)")
                );
            } else {
                addWorldTime(world, ticks, newTime ->
                        sender.sendMessage("Added " + ticks + " ticks in " + world.getName() + ". New time: " + formatTime(newTime))
                );
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.time")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.time"));
            return true;
        }

        if (args.length == 0) {
            World world = player.getWorld();
            long ticks = world.getTime();
            String formatted = formatTime(ticks);
            MessageUtil.send(player, "utilities.time.get",
                    Map.of("ticks", String.valueOf(ticks),
                            "time-formatted", formatted));
            return true;
        }

        String sub = args[0].toLowerCase();

        // /time set
        if (sub.equals("set")) {
            if (args.length < 2) {
                sendUsage(player, label, "set");
                return true;
            }

            long ticks;
            String option = args[1].toLowerCase();
            switch (option) {
                case "day" -> ticks = 1000;
                case "noon" -> ticks = 6000;
                case "night" -> ticks = 13000;
                case "midnight" -> ticks = 18000;
                default -> {
                    Long parsed = TimeUtil.parseDurationTicks(option);
                    if (parsed == null) {
                        MessageUtil.send(player, "errors.invalid-format", Map.of());
                        return true;
                    }
                    ticks = parsed;
                }
            }

            if (args.length == 3) {
                if (!player.hasPermission("aircore.command.time.world")) {
                    MessageUtil.send(player, "errors.no-permission",
                            Map.of("permission", "aircore.command.time.world"));
                    return true;
                }
                World world = Bukkit.getWorld(args[2]);
                if (world == null) {
                    MessageUtil.send(player, "errors.world-not-found",
                            Map.of("world", args[2]));
                    return true;
                }

                setWorldTime(world, ticks, () ->
                        plugin.scheduler().runEntityTask(player, () ->
                                MessageUtil.send(player, "utilities.time.set-in",
                                        Map.of("ticks", String.valueOf(ticks),
                                                "time-formatted", formatTime(ticks),
                                                "world", world.getName()))
                        )
                );

            } else if (args.length > 3) {
                MessageUtil.send(player, "errors.invalid-format", Map.of());
                return true;
            } else {
                World world = player.getWorld();

                setWorldTime(world, ticks, () ->
                        plugin.scheduler().runEntityTask(player, () ->
                                MessageUtil.send(player, "utilities.time.set",
                                        Map.of("ticks", String.valueOf(ticks),
                                                "time-formatted", formatTime(ticks))))
                );
            }
            return true;
        }

        // /time add
        if (sub.equals("add")) {
            if (args.length < 2) {
                sendUsage(player, label, "add");
                return true;
            }

            Long parsed = TimeUtil.parseDurationTicks(args[1]);
            if (parsed == null) {
                MessageUtil.send(player, "errors.invalid-format", Map.of());
                return true;
            }
            long ticks = parsed;

            if (args.length == 3) {
                if (!player.hasPermission("aircore.command.time.world")) {
                    MessageUtil.send(player, "errors.no-permission",
                            Map.of("permission", "aircore.command.time.world"));
                    return true;
                }
                World world = Bukkit.getWorld(args[2]);
                if (world == null) {
                    MessageUtil.send(player, "errors.world-not-found",
                            Map.of("world", args[2]));
                    return true;
                }

                addWorldTime(world, ticks, newTime ->
                        plugin.scheduler().runEntityTask(player, () ->
                                MessageUtil.send(player, "utilities.time.add-in",
                                        Map.of("ticks", String.valueOf(ticks),
                                                "time-formatted", formatTime(newTime),
                                                "world", world.getName()))
                        )
                );

            } else if (args.length > 3) {
                MessageUtil.send(player, "errors.invalid-format", Map.of());
                return true;
            } else {
                World world = player.getWorld();
                addWorldTime(world, ticks, newTime ->
                        plugin.scheduler().runEntityTask(player, () ->
                                MessageUtil.send(player, "utilities.time.add",
                                        Map.of("ticks", String.valueOf(ticks),
                                                "time-formatted", formatTime(newTime))))
                );
            }
            return true;
        }

        sendUsage(player, label, "");
        return true;
    }

    private void setWorldTime(World world, long ticks, Runnable post) {
        plugin.scheduler().runTask(() -> {
            world.setTime(ticks);
            if (post != null) post.run();
        });
    }

    private void addWorldTime(World world, long ticks, Consumer<Long> post) {
        plugin.scheduler().runTask(() -> {
            long newTime = world.getTime() + ticks;
            world.setTime(newTime);
            if (post != null) post.accept(newTime);
        });
    }

    private void sendUsage(Player player, String label, String sub) {
        if (player.hasPermission("aircore.command.time.world")) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("time", sub.isEmpty() ? "world" : sub + "-world", label)));
        } else {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("time", sub.isEmpty() ? "" : sub, label)));
        }
    }

    private String formatTime(long ticks) {
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.time")) {
                return List.of();
            }
        }

        if (args.length == 1) {
            return Stream.of("set", "add")
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Stream.of("day", "noon", "night", "midnight")
                    .filter(opt -> opt.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            if (!(sender instanceof Player) || sender.hasPermission("aircore.command.time.world")) {
                return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
