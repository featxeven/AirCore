package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class PlayerTimeCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.ptime";
    private static final String PERMISSION_OTHERS = "aircore.command.ptime.others";

    public PlayerTimeCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length == 0) {
            sendError(sender, label, null, "incorrect-usage");
            return true;
        }

        String setSel = plugin.commandConfig().getSelector("ptime", "set");
        String addSel = plugin.commandConfig().getSelector("ptime", "add");
        String resetSel = plugin.commandConfig().getSelector("ptime", "reset");

        String variant = getVariant(args[0], setSel, addSel, resetSel);
        if (variant == null) {
            sendError(sender, label, null, "incorrect-usage");
            return true;
        }

        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || !(sender instanceof Player);

        int maxArgs = variant.equals("reset") ? (hasOthers ? 2 : 1) : (hasOthers ? 3 : 2);

        if (args.length > maxArgs) {
            sendError(sender, label, variant, "too-many-arguments");
            return true;
        }

        long ticks = 0;
        int targetIdx = 1;

        if (!variant.equals("reset")) {
            if (args.length < 2) {
                sendError(sender, label, variant, "incorrect-usage");
                return true;
            }
            ticks = parseTicks(args[1]);
            if (ticks == -1) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.invalid-number", Map.of("input", args[1]));
                else sender.sendMessage("Invalid number format: " + args[1]);
                return true;
            }
            targetIdx = 2;
        }

        OfflinePlayer target;
        if (args.length > targetIdx) {
            target = resolve(sender, args[targetIdx]);
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Console must specify a player.");
                return true;
            }
            target = p;
        }

        if (target == null) return true;
        applyPlayerTime(sender, target, variant, ticks);
        return true;
    }

    private void sendError(CommandSender sender, String label, String variant, String key) {
        if (sender instanceof Player p) {
            boolean hasOthers = p.hasPermission(PERMISSION_OTHERS);
            String variantKey = null;

            if (variant != null && (variant.equals("set") || variant.equals("add"))) {
                variantKey = variant;
            }

            if (hasOthers) {
                variantKey = (variantKey == null) ? "others" : variantKey + "-others";
            }

            String usage = plugin.commandConfig().getUsage("ptime", variantKey, label);
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            sender.sendMessage("Usage: /" + label + " <set|add|reset> <time> [player]");
        }
    }

    private String getVariant(String input, String setSel, String addSel, String resetSel) {
        if (input.equalsIgnoreCase(setSel)) return "set";
        if (input.equalsIgnoreCase(addSel)) return "add";
        if (input.equalsIgnoreCase(resetSel)) return "reset";
        return null;
    }

    private void applyPlayerTime(CommandSender sender, OfflinePlayer target, String variant, long ticks) {
        UUID uuid = target.getUniqueId();
        long finalTicks;

        if (variant.equals("reset")) {
            finalTicks = -1L;
            plugin.database().records().setPlayerTime(uuid, -1L);
        } else {
            long current = (target.isOnline() && target.getPlayer() != null) ? target.getPlayer().getPlayerTime() : 0L;
            finalTicks = variant.equals("add") ? current + ticks : ticks;
            plugin.database().records().setPlayerTime(uuid, finalTicks);
        }

        if (target.isOnline() && target.getPlayer() != null) {
            Player online = target.getPlayer();
            plugin.scheduler().runEntityTask(online, () -> {
                if (finalTicks == -1L) online.resetPlayerTime();
                else online.setPlayerTime(finalTicks, false);
            });
        }

        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String timeFormatted = formatTime(finalTicks);

        if (sender instanceof Player p) {
            boolean isSelf = uuid.equals(p.getUniqueId());
            String path = "utilities.time.player." + variant + (isSelf ? "" : "-for");

            if (!isSelf && target.isOnline() && target.getPlayer() != null) {
                MessageUtil.send(target.getPlayer(), "utilities.time.player." + variant + "-by", Map.of(
                        "ticks", variant.equals("reset") ? "default" : String.valueOf(ticks),
                        "time-formatted", timeFormatted,
                        "player", senderName
                ));
            }

            MessageUtil.send(p, path, Map.of(
                    "ticks", variant.equals("reset") ? "default" : String.valueOf(variant.equals("add") ? ticks : finalTicks),
                    "time-formatted", timeFormatted,
                    "player", targetName
            ));
        } else {
            sender.sendMessage("Applied player-time " + variant + " for " + targetName);
        }
    }

    private long parseTicks(String input) {
        String lower = input.toLowerCase();
        return switch (lower) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                Long parsed = TimeUtil.parseDurationTicks(lower);
                if (parsed != null) yield parsed;
                try {
                    yield Long.parseLong(lower);
                } catch (NumberFormatException e) {
                    yield -1L;
                }
            }
        };
    }

    private String formatTime(long ticks) {
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);
        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();
        String input = args[args.length - 1].toLowerCase();
        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS);

        String setSel = plugin.commandConfig().getSelector("ptime", "set");
        String addSel = plugin.commandConfig().getSelector("ptime", "add");
        String resetSel = plugin.commandConfig().getSelector("ptime", "reset");

        if (args.length == 1) {
            return Stream.of(setSel, addSel, resetSel).filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase(resetSel)) {
                if (!hasOthers) return Collections.emptyList();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(input)).limit(20).toList();
            }
            if (args[0].equalsIgnoreCase(setSel)) {
                return Stream.of("day", "noon", "night", "midnight").filter(s -> s.startsWith(input)).toList();
            }
            return Collections.emptyList();
        }

        if (args.length == 3 && hasOthers && !args[0].equalsIgnoreCase(resetSel)) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(input)).limit(20).toList();
        }

        return Collections.emptyList();
    }
}