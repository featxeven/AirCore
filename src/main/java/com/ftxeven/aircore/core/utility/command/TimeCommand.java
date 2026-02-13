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

        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <set|add> <time> [world]");
                return true;
            }

            String type = args[0].toLowerCase();
            long ticks = parseTicks(args[1]);

            if (!type.equals("set") && !type.equals("add")) {
                sender.sendMessage("Usage: /" + label + " <set|add> <time> [world]");
                return true;
            }

            if (ticks == -1) {
                sender.sendMessage("Invalid time format.");
                return true;
            }

            World world = args.length >= 3 ? Bukkit.getWorld(args[2]) : Bukkit.getWorlds().getFirst();
            if (world == null) {
                sender.sendMessage("World not found.");
                return true;
            }

            executeTimeLogic(sender, type, world, ticks, args.length >= 3);
            return true;
        }

        if (!player.hasPermission("aircore.command.time")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.time"));
            return true;
        }

        if (args.length == 0) {
            World world = player.getWorld();
            long ticks = world.getTime();
            MessageUtil.send(player, "utilities.time.get",
                    Map.of("ticks", String.valueOf(ticks), "time-formatted", formatTime(ticks)));
            return true;
        }

        boolean hasWorldPerm = player.hasPermission("aircore.command.time.world");
        String sub = args[0].toLowerCase();

        if (args.length >= 3 && !hasWorldPerm) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.time.world"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs()) {
            int limit = hasWorldPerm ? 3 : 2;
            if (args.length > limit) {
                sendTooManyArgs(player, label, sub);
                return true;
            }
        }

        if (!sub.equals("set") && !sub.equals("add")) {
            sendUsage(player, label, "");
            return true;
        }

        if (args.length < 2) {
            sendUsage(player, label, sub);
            return true;
        }

        long ticks = parseTicks(args[1]);
        if (ticks == -1) {
            MessageUtil.send(player, "errors.invalid-format", Map.of());
            return true;
        }

        World targetWorld = player.getWorld();
        if (args.length == 3) {
            targetWorld = Bukkit.getWorld(args[2]);
            if (targetWorld == null) {
                MessageUtil.send(player, "errors.world-not-found", Map.of("world", args[2]));
                return true;
            }
        }

        executeTimeLogic(player, sub, targetWorld, ticks, args.length == 3);
        return true;
    }

    private void executeTimeLogic(CommandSender sender, String sub, World world, long ticks, boolean isWorldSpecific) {
        if (sub.equals("set")) {
            setWorldTime(world, ticks, () -> {
                if (sender instanceof Player p) {
                    plugin.scheduler().runEntityTask(p, () -> {
                        String key = isWorldSpecific ? "utilities.time.set-in" : "utilities.time.set";
                        MessageUtil.send(p, key, Map.of(
                                "ticks", String.valueOf(ticks),
                                "time-formatted", formatTime(ticks),
                                "world", world.getName()
                        ));
                    });
                } else {
                    sender.sendMessage("Set time in " + world.getName() + " to " + formatTime(ticks));
                }
            });
        } else {
            addWorldTime(world, ticks, newTime -> {
                if (sender instanceof Player p) {
                    plugin.scheduler().runEntityTask(p, () -> {
                        String key = isWorldSpecific ? "utilities.time.add-in" : "utilities.time.add";
                        MessageUtil.send(p, key, Map.of(
                                "ticks", String.valueOf(ticks),
                                "time-formatted", formatTime(newTime),
                                "world", world.getName()
                        ));
                    });
                } else {
                    sender.sendMessage("Added time in " + world.getName() + ". New time: " + formatTime(newTime));
                }
            });
        }
    }

    private long parseTicks(String input) {
        return switch (input.toLowerCase()) {
            case "day" -> 1000;
            case "noon" -> 6000;
            case "night" -> 13000;
            case "midnight" -> 18000;
            default -> {
                Long parsed = TimeUtil.parseDurationTicks(input);
                yield (parsed != null) ? parsed : -1L;
            }
        };
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
        String key = sub.isEmpty() ? "" : sub;
        if (player.hasPermission("aircore.command.time.world")) {
            key = sub.isEmpty() ? "world" : sub + "-world";
        }
        MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("time", key, label)));
    }

    private void sendTooManyArgs(Player player, String label, String sub) {
        String base = (sub.equals("set") || sub.equals("add")) ? sub : "";
        String key = (base.isEmpty() ? "world" : base + "-world");
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("time", key, label)));
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
        String input = args[args.length - 1].toLowerCase();

        if (sender instanceof Player player && !player.hasPermission("aircore.command.time")) {
            return List.of();
        }

        if (args.length == 1) {
            return Stream.of("set", "add").filter(opt -> opt.startsWith(input)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Stream.of("day", "noon", "night", "midnight").filter(opt -> opt.startsWith(input)).toList();
        }

        if (args.length == 3) {
            if (!(sender instanceof Player player) || player.hasPermission("aircore.command.time.world")) {
                return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .toList();
            }
        }

        return List.of();
    }
}