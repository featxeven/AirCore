package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.WeatherType;
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

public final class PlayerWeatherCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.pweather";
    private static final String PERMISSION_OTHERS = "aircore.command.pweather.others";

    public PlayerWeatherCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || !(sender instanceof Player);

        if (args.length == 0) {
            sendError(sender, label, hasOthers, "incorrect-usage");
            return true;
        }

        String clearSel = plugin.commandConfig().getSelector("pweather", "clear");
        String rainSel = plugin.commandConfig().getSelector("pweather", "rain");
        String thunderSel = plugin.commandConfig().getSelector("pweather", "thunder");
        String resetSel = plugin.commandConfig().getSelector("pweather", "reset");

        String type = getWeatherType(args[0], clearSel, rainSel, thunderSel, resetSel);
        if (type == null) {
            sendError(sender, label, hasOthers, "incorrect-usage");
            return true;
        }

        int maxArgs = hasOthers ? 2 : 1;
        if (args.length > maxArgs) {
            sendError(sender, label, hasOthers, "too-many-arguments");
            return true;
        }

        OfflinePlayer target;
        if (args.length > 1) {
            target = resolve(sender, args[1]);
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Console must specify a player.");
                return true;
            }
            target = p;
        }

        if (target == null) return true;
        applyPlayerWeather(sender, target, type);
        return true;
    }

    private String getWeatherType(String input, String clear, String rain, String thunder, String reset) {
        if (input.equalsIgnoreCase(clear)) return "clear";
        if (input.equalsIgnoreCase(rain)) return "rain";
        if (input.equalsIgnoreCase(thunder)) return "thunder";
        if (input.equalsIgnoreCase(reset)) return "reset";
        return null;
    }

    private void applyPlayerWeather(CommandSender sender, OfflinePlayer target, String type) {
        String typeDisplay = String.valueOf(plugin.lang().get("utilities.weather.placeholders." + type));
        UUID uuid = target.getUniqueId();

        plugin.database().records().setPlayerWeather(uuid, type);

        if (target.isOnline() && target.getPlayer() != null) {
            Player online = target.getPlayer();
            plugin.scheduler().runEntityTask(online, () -> {
                if (type.equals("reset")) online.resetPlayerWeather();
                else online.setPlayerWeather(type.equals("clear") ? WeatherType.CLEAR : WeatherType.DOWNFALL);
            });
        }

        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        if (sender instanceof Player p) {
            boolean isSelf = uuid.equals(p.getUniqueId());
            String action = type.equals("reset") ? "reset" : "set";
            String path = "utilities.weather.player." + action + (isSelf ? "" : "-for");

            if (!isSelf && target.isOnline() && target.getPlayer() != null) {
                MessageUtil.send(target.getPlayer(), "utilities.weather.player." + action + "-by", Map.of(
                        "type", typeDisplay,
                        "player", senderName
                ));
            }
            MessageUtil.send(p, path, Map.of("type", typeDisplay, "player", targetName));
        } else {
            sender.sendMessage("Set player-weather " + type + " for " + targetName);
        }
    }

    private void sendError(CommandSender sender, String label, boolean hasOthers, String key) {
        if (sender instanceof Player p) {
            String usage = plugin.commandConfig().getUsage("pweather", hasOthers ? "others" : null, label);
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            sender.sendMessage("Usage: /" + label + " <type> [player]");
        }
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

        if (args.length == 1) {
            return Stream.of(
                    plugin.commandConfig().getSelector("pweather", "clear"),
                    plugin.commandConfig().getSelector("pweather", "rain"),
                    plugin.commandConfig().getSelector("pweather", "thunder"),
                    plugin.commandConfig().getSelector("pweather", "reset")
            ).filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2 && sender.hasPermission(PERMISSION_OTHERS)) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(input)).limit(20).toList();
        }

        return Collections.emptyList();
    }
}