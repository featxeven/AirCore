package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        if (type == null) return plugin.lang().get("utilities.speed.placeholders.both");
        return type.equals("flying") ? plugin.lang().get("utilities.speed.placeholders.flying") : plugin.lang().get("utilities.speed.placeholders.walking");
    }

    private void applySpeedToOffline(UUID uuid, String type, double value) {
        if (type == null) {
            plugin.database().records().setSpeed(uuid, "walking", value);
            plugin.database().records().setSpeed(uuid, "flying", value);
        } else {
            plugin.database().records().setSpeed(uuid, type, value);
        }
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
        plugin.database().records().setSpeed(target.getUniqueId(), type, value);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

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

            double value = parseSpeed(sender, args[index]);
            if (value < 0) return true;

            if (args.length <= index + 1) {
                sender.sendMessage("Usage: /" + label + " [walking|flying] <speed> <player>");
                return true;
            }

            for (int i = index + 1; i < args.length; i++) {
                processSpeedChange(sender, args[i], type, value, consoleName, true);
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.speed")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.speed"));
            return true;
        }

        if (args.length < 1) {
            String usageKey = player.hasPermission("aircore.command.speed.others") ? "others" : null;
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("speed", usageKey, label)));
            return true;
        }

        String type = null;
        int index = 0;
        if (args[0].equalsIgnoreCase("walking") || args[0].equalsIgnoreCase("flying")) {
            type = args[0].toLowerCase();
            index++;
        }

        if (args.length <= index) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("speed", label)));
            return true;
        }

        double value = parseSpeed(player, args[index]);
        if (value < 0) return true;

        if (player.hasPermission("aircore.command.speed.others") && args.length > index + 1) {
            for (int i = index + 1; i < args.length; i++) {
                processSpeedChange(player, args[i], type, value, player.getName(), false);
            }
            return true;
        }

        // Self behavior
        String finalType = (type == null) ? (player.isFlying() ? "flying" : "walking") : type;
        applySpeed(player, finalType, value);
        MessageUtil.send(player, "utilities.speed.set",
                Map.of("type", formatType(finalType), "speed", formatSpeed(value)));

        return true;
    }

    private double parseSpeed(CommandSender sender, String input) {
        try {
            double value = Double.parseDouble(input);
            if (value < MIN_SPEED || value > MAX_SPEED) {
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "utilities.speed.limit", Map.of("min", "0", "max", String.valueOf((int) MAX_SPEED)));
                } else {
                    sender.sendMessage("Speed must be between 0 and " + (int) MAX_SPEED);
                }
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            if (sender instanceof Player p) MessageUtil.send(p, "errors.invalid-format", Map.of());
            else sender.sendMessage("Invalid speed value.");
            return -1;
        }
    }

    private void processSpeedChange(CommandSender sender, String targetName, String type, double value, String senderName, boolean isConsole) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        String displayName = resolved.getName() != null ? resolved.getName() : targetName;

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player targetPlayer = resolved.getPlayer();
            // If online and type is null, we determine it based on their current state
            String finalType = (type == null) ? (targetPlayer.isFlying() ? "flying" : "walking") : type;

            applySpeed(targetPlayer, finalType, value);

            if (!isConsole && sender instanceof Player p) {
                if (targetPlayer.equals(p)) {
                    MessageUtil.send(p, "utilities.speed.set", Map.of("type", formatType(finalType), "speed", formatSpeed(value)));
                } else {
                    MessageUtil.send(p, "utilities.speed.set-for", Map.of("type", formatType(finalType), "speed", formatSpeed(value), "player", displayName));
                    MessageUtil.send(targetPlayer, "utilities.speed.set-by", Map.of("player", senderName, "type", formatType(finalType), "speed", formatSpeed(value)));
                }
            } else {
                sender.sendMessage("Set " + formatType(finalType) + " speed for " + displayName + " -> " + formatSpeed(value));
                if (plugin.config().consoleToPlayerFeedback()) {
                    MessageUtil.send(targetPlayer, "utilities.speed.set-by", Map.of("player", senderName, "type", formatType(finalType), "speed", formatSpeed(value)));
                }
            }
        } else {
            applySpeedToOffline(resolved.getUniqueId(), type, value);
            String typeDisplay = formatType(type);

            if (!isConsole && sender instanceof Player p) {
                MessageUtil.send(p, "utilities.speed.set-for", Map.of("type", typeDisplay, "speed", formatSpeed(value), "player", displayName));
            } else {
                sender.sendMessage("Set " + typeDisplay + " speed for " + displayName + " (Offline) -> " + formatSpeed(value));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            if (sender instanceof Player p && !p.hasPermission("aircore.command.speed")) return List.of();
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

    private OfflinePlayer resolve(CommandSender sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        else sender.sendMessage("Player not found.");
        return null;
    }
}