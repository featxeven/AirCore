package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpeedCommand implements TabExecutor {

    private static final String PERMISSION = "aircore.command.speed";
    private static final String PERMISSION_OTHERS = "aircore.command.speed.others";
    private static final double MIN_SPEED = 0.0;
    private static final double MAX_SPEED = 10.0;
    private final AirCore plugin;

    public SpeedCommand(AirCore plugin) {
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

        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || !(sender instanceof Player);
        int maxArgs = hasOthers ? 3 : 2;

        if (args.length > maxArgs) {
            sendError(sender, label, "too-many-arguments");
            return true;
        }

        if (args.length == 0) {
            sendError(sender, label, "incorrect-usage");
            return true;
        }

        double value = parseSpeed(sender, args[0]);
        if (value < 0) return true;

        String flySel = plugin.commandConfig().getSelector("speed", "-flying");
        String walkSel = plugin.commandConfig().getSelector("speed", "-walking");
        String type = null;
        String targetName = null;

        if (args.length >= 2) {
            String arg1 = args[1].toLowerCase();
            boolean isType = arg1.equals(flySel) || arg1.equals(walkSel);

            if (isType) {
                type = arg1.equals(flySel) ? "flying" : "walking";
                if (args.length == 3) {
                    targetName = args[2];
                }
            } else {
                if (!hasOthers) {
                    sendError(sender, label, "incorrect-usage");
                    return true;
                }
                targetName = arg1;
            }
        }

        if (targetName != null) {
            processSpeedChange(sender, targetName, type, value);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /" + label + " <speed> [" + walkSel + "|" + flySel + "] <player>");
            return true;
        }

        String finalType = (type == null) ? (player.isFlying() ? "flying" : "walking") : type;
        applySpeed(player, finalType, value);
        MessageUtil.send(player, "utilities.speed.set", Map.of("type", formatType(finalType), "speed", formatSpeed(value)));

        return true;
    }

    private void sendError(CommandSender sender, String label, String key) {
        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || !(sender instanceof Player);
        String usage = plugin.commandConfig().getUsage("speed", hasOthers ? "others" : null, label);
        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            String walkSel = plugin.commandConfig().getSelector("speed", "walking");
            String flySel = plugin.commandConfig().getSelector("speed", "flying");
            sender.sendMessage("Usage: /" + label + " <speed> [" + walkSel + "|" + flySel + "] <player>");
        }
    }

    private double parseSpeed(CommandSender sender, String input) {
        try {
            double value = Double.parseDouble(input);
            if (value < MIN_SPEED || value > MAX_SPEED) {
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "utilities.speed.limit", Map.of("min", "0", "max", "10"));
                } else {
                    sender.sendMessage("Speed must be 0-10");
                }
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            if (sender instanceof Player p) MessageUtil.send(p, "errors.invalid-format", Map.of());
            else sender.sendMessage("Invalid speed value");
            return -1;
        }
    }

    private void processSpeedChange(CommandSender sender, String targetName, String type, double value) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        String realName = plugin.database().records().getRealName(targetName);
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player targetPlayer = resolved.getPlayer();
            String finalType = (type == null) ? (targetPlayer.isFlying() ? "flying" : "walking") : type;
            applySpeed(targetPlayer, finalType, value);

            if (sender instanceof Player p) {
                if (targetPlayer.equals(p)) {
                    MessageUtil.send(p, "utilities.speed.set", Map.of("type", formatType(finalType), "speed", formatSpeed(value)));
                } else {
                    MessageUtil.send(p, "utilities.speed.set-for", Map.of("type", formatType(finalType), "speed", formatSpeed(value), "player", realName));
                    MessageUtil.send(targetPlayer, "utilities.speed.set-by", Map.of("player", senderName, "type", formatType(finalType), "speed", formatSpeed(value)));
                }
            } else {
                sender.sendMessage("Set " + finalType + " speed for " + realName + " to " + formatSpeed(value));
                if (plugin.config().consoleToPlayerFeedback()) {
                    MessageUtil.send(targetPlayer, "utilities.speed.set-by", Map.of("player", senderName, "type", formatType(finalType), "speed", formatSpeed(value)));
                }
            }
        } else {
            applySpeedToOffline(resolved.getUniqueId(), type, value);
            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.speed.set-for", Map.of("type", formatType(type), "speed", formatSpeed(value), "player", realName));
            } else {
                String typeDisplay = (type == null) ? "both" : type;
                sender.sendMessage("Set offline " + typeDisplay + " speed for " + realName + " to " + formatSpeed(value));
            }
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);
        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        else sender.sendMessage("Player not found");
        return null;
    }

    private void applySpeed(Player target, String type, double value) {
        float apiValue = (float) ("flying".equals(type) ? (value * 0.1) : (value * 0.2));
        plugin.scheduler().runEntityTask(target, () -> {
            if (type.equals("flying")) target.setFlySpeed(Math.min(1.0f, apiValue));
            else target.setWalkSpeed(Math.min(1.0f, apiValue));
        });
        plugin.database().records().setSpeed(target.getUniqueId(), type, value);
    }

    private String formatSpeed(double value) {
        return (value == Math.floor(value)) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private String formatType(String type) {
        if (type == null) return String.valueOf(plugin.lang().get("utilities.speed.placeholders.both"));
        return type.equals("flying") ? String.valueOf(plugin.lang().get("utilities.speed.placeholders.flying")) : String.valueOf(plugin.lang().get("utilities.speed.placeholders.walking"));
    }

    private void applySpeedToOffline(UUID uuid, String type, double value) {
        if (type == null) {
            plugin.database().records().setSpeed(uuid, "walking", value);
            plugin.database().records().setSpeed(uuid, "flying", value);
        } else {
            plugin.database().records().setSpeed(uuid, type, value);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();
        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || !(sender instanceof Player);

        if (args.length == 1) return Collections.emptyList();

        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add(plugin.commandConfig().getSelector("speed", "flying"));
            suggestions.add(plugin.commandConfig().getSelector("speed", "walking"));
            if (hasOthers) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(input))
                        .forEach(suggestions::add);
            }
            return suggestions.stream().filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 3 && hasOthers) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}