package com.ftxeven.aircore.core.utility.command;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class SpeedCommand implements TabExecutor {

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

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        if (sender instanceof Player p && !p.hasPermission("aircore.command.speed")) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", "aircore.command.speed"));
            return true;
        }

        if (sender instanceof Player p && plugin.config().errorOnExcessArgs()) {
            boolean hasOthers = p.hasPermission("aircore.command.speed.others");
            int limit = hasOthers ? 3 : 2;

            if (args.length > limit) {
                if (hasOthers) {
                    MessageUtil.send(p, "errors.too-many-arguments",
                            Map.of("usage", plugin.config().getUsage("speed", "others", label)));
                } else {
                    MessageUtil.send(p, "errors.too-many-arguments",
                            Map.of("usage", plugin.config().getUsage("speed", label)));
                }
                return true;
            }
        }

        double value = parseSpeed(sender, args[0]);
        if (value < 0) return true;

        String type = null;
        String targetName = null;

        if (args.length >= 2) {
            String secondArg = args[1].toLowerCase();
            if (secondArg.equals("walking") || secondArg.equals("flying")) {
                type = secondArg;
                if (args.length >= 3) {
                    targetName = args[2];
                }
            } else {
                targetName = args[1];
            }
        }

        if (targetName != null) {
            if (sender instanceof Player p && !p.hasPermission("aircore.command.speed.others")) {
                MessageUtil.send(p, "errors.no-permission", Map.of("permission", "aircore.command.speed.others"));
                return true;
            }

            processSpeedChange(sender, targetName, type, value);
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /" + label + " <speed> [walking|flying] <player>");
                return true;
            }

            String finalType = (type == null) ? (player.isFlying() ? "flying" : "walking") : type;
            applySpeed(player, finalType, value);
            MessageUtil.send(player, "utilities.speed.set",
                    Map.of("type", formatType(finalType), "speed", formatSpeed(value)));
        }

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        if (sender instanceof Player p) {
            if (p.hasPermission("aircore.command.speed.others")) {
                MessageUtil.send(p, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("speed", "others", label)));
            } else {
                MessageUtil.send(p, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("speed", label)));
            }
        } else {
            sender.sendMessage("Usage: /" + label + " <speed> [walking|flying] <player>");
        }
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

    private void processSpeedChange(CommandSender sender, String targetName, String type, double value) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        String displayName = resolved.getName() != null ? resolved.getName() : targetName;
        String senderName = (sender instanceof Player p) ? p.getName() : plugin.lang().get("general.console-name");

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player targetPlayer = resolved.getPlayer();
            String finalType = (type == null) ? (targetPlayer.isFlying() ? "flying" : "walking") : type;

            applySpeed(targetPlayer, finalType, value);

            if (sender instanceof Player p) {
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

            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.speed.set-for", Map.of("type", typeDisplay, "speed", formatSpeed(value), "player", displayName));
            } else {
                sender.sendMessage("Set " + typeDisplay + " speed for " + displayName + " (Offline) -> " + formatSpeed(value));
            }
        }
    }

    private OfflinePlayer resolveSilent(String name) {
        if (name == null) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        return (cached != null) ? Bukkit.getOfflinePlayer(cached) : null;
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        OfflinePlayer found = resolveSilent(name);
        if (found != null) return found;

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of("player", name));
        } else {
            sender.sendMessage("Player not found in database.");
        }
        return null;
    }

    private void applySpeed(Player target, String type, double value) {
        float apiValue = toApiSpeed(type, value);
        plugin.scheduler().runEntityTask(target, () -> {
            if (type.equals("flying")) target.setFlySpeed(apiValue);
            else target.setWalkSpeed(apiValue);
        });
        plugin.database().records().setSpeed(target.getUniqueId(), type, value);
    }

    private float toApiSpeed(String type, double value) {
        double scaled = "flying".equals(type) ? (value * 0.1) : (value * 0.2);
        return (float) Math.min(1.0, Math.max(0.0, scaled));
    }

    private String formatSpeed(double value) {
        return (value == Math.floor(value)) ? String.valueOf((int) value) : String.valueOf(value);
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String input = args[args.length - 1].toLowerCase();
        if (args.length == 1) return Collections.emptyList();

        boolean isConsole = !(sender instanceof Player);
        boolean hasOthers = isConsole || sender.hasPermission("aircore.command.speed.others");

        if (args.length == 2) {
            List<String> completions = new ArrayList<>(Stream.of("walking", "flying")
                    .filter(s -> s.startsWith(input)).toList());

            if (hasOthers) {
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
                });
            }
            return completions;
        }

        if (args.length == 3 && hasOthers) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }
        return Collections.emptyList();
    }
}