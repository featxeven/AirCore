package com.ftxeven.aircore.core.module.utility.command;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class TimeCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.time";

    public TimeCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        String setSel = plugin.commandConfig().getSelector("time", "set");
        String addSel = plugin.commandConfig().getSelector("time", "add");

        if (args.length > 3) {
            String variant = getVariant(args[0], setSel, addSel);
            sendError(sender, label, variant, "too-many-arguments");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /" + label + " <" + setSel + "|" + addSel + "> <time> [world]");
                return true;
            }
            long ticks = player.getWorld().getTime();
            MessageUtil.send(player, "utilities.time.get", Map.of(
                    "ticks", String.valueOf(ticks),
                    "time-formatted", formatTime(ticks)
            ));
            return true;
        }

        String inputSub = args[0].toLowerCase();
        String variant = getVariant(inputSub, setSel, addSel);

        if (variant == null) {
            sendError(sender, label, null, "incorrect-usage");
            return true;
        }

        if (args.length < 2) {
            sendError(sender, label, variant, "incorrect-usage");
            return true;
        }

        long ticks = parseTicks(args[1]);
        if (ticks == -1) {
            if (sender instanceof Player p) MessageUtil.send(p, "errors.invalid-format", Map.of());
            else sender.sendMessage("Invalid time format: " + args[1]);
            return true;
        }

        World targetWorld = resolveWorld(sender, args, label, setSel, addSel);
        if (targetWorld == null) return true;

        executeTimeLogic(sender, variant, targetWorld, ticks, args.length == 3);
        return true;
    }

    private String getVariant(String input, String setSel, String addSel) {
        if (input.equalsIgnoreCase(setSel)) return "set";
        if (input.equalsIgnoreCase(addSel)) return "add";
        return null;
    }

    private World resolveWorld(CommandSender sender, String[] args, String label, String setSel, String addSel) {
        if (args.length == 3) {
            World world = Bukkit.getWorld(args[2]);
            if (world == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.world-not-found", Map.of("world", args[2]));
                else sender.sendMessage("World not found: " + args[2]);
            }
            return world;
        }

        if (sender instanceof Player p) return p.getWorld();

        sender.sendMessage("Usage: /" + label + " <" + setSel + "|" + addSel + "> <time> <world>");
        return null;
    }

    private void executeTimeLogic(CommandSender sender, String variant, World world, long amount, boolean isWorldSpecific) {
        plugin.scheduler().runTask(() -> {
            long finalTime;
            if (variant.equals("set")) {
                finalTime = amount;
            } else {
                finalTime = world.getTime() + amount;
            }

            world.setTime(finalTime);

            if (sender instanceof Player p) {
                String key = "utilities.time." + variant + (isWorldSpecific ? "-in" : "");
                MessageUtil.send(p, key, Map.of(
                        "ticks", String.valueOf(amount),
                        "time-formatted", formatTime(finalTime),
                        "world", world.getName()
                ));
            } else {
                sender.sendMessage("Updated time in " + world.getName() + " to " + formatTime(finalTime));
            }
        });
    }

    private long parseTicks(String input) {
        return switch (input.toLowerCase()) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                Long parsed = TimeUtil.parseDurationTicks(input.toLowerCase());
                yield (parsed != null) ? parsed : -1L;
            }
        };
    }

    private void sendError(CommandSender sender, String label, String variant, String key) {
        if (sender instanceof Player p) {
            String usage = plugin.commandConfig().getUsage("time", variant, label);
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            String set = plugin.commandConfig().getSelector("time", "set");
            String add = plugin.commandConfig().getSelector("time", "add");
            sender.sendMessage("Usage: /" + label + " <" + set + "|" + add + "> <time> <world>");
        }
    }

    private String formatTime(long ticks) {
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();
        String setSel = plugin.commandConfig().getSelector("time", "set");
        String addSel = plugin.commandConfig().getSelector("time", "add");

        if (args.length == 1) {
            return Stream.of(setSel, addSel).filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase(setSel)) {
            return Stream.of("day", "noon", "night", "midnight").filter(opt -> opt.startsWith(input)).toList();
        }

        if (args.length == 3) {
            return Bukkit.getWorlds().stream().map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(input)).limit(20).toList();
        }

        return Collections.emptyList();
    }
}