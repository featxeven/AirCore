package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.WeatherType;
import org.bukkit.World;
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
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        boolean isConsole = !(sender instanceof Player);
        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS) || isConsole;

        int maxArgs = hasOthers ? 3 : 2;
        if (args.length > maxArgs) {
            sendError(sender, label, hasOthers, "too-many-arguments");
            return true;
        }

        if (args.length == 0) {
            sendError(sender, label, hasOthers, "incorrect-usage");
            return true;
        }

        String clearSel = plugin.commandConfig().getSelector("pweather", "clear");
        String thunderSel = plugin.commandConfig().getSelector("pweather", "thunder");
        String resetSel = plugin.commandConfig().getSelector("pweather", "reset");

        String inputType = args[0].toLowerCase();
        String type;

        if (inputType.equals(clearSel)) type = "clear";
        else if (inputType.equals(thunderSel)) type = "thunder";
        else if (inputType.equals(resetSel)) type = "reset";
        else {
            sendError(sender, label, hasOthers, "incorrect-usage");
            return true;
        }

        if (isConsole && args.length != 3) {
            sender.sendMessage("Usage: /" + label + " <type> <world> <player>");
            return true;
        }

        OfflinePlayer target;
        World world;
        boolean worldWasSpecified = false;

        if (args.length == 1) {
            target = (Player) sender;
            world = ((Player) sender).getWorld();
        } else if (args.length == 2) {
            world = Bukkit.getWorld(args[1]);
            if (world != null) {
                worldWasSpecified = true;
                target = (Player) sender;
            } else {
                if (hasOthers) {
                    target = resolve(sender, args[1]);
                    if (target == null) return true;
                    world = ((Player) sender).getWorld();
                } else {
                    MessageUtil.send((Player) sender, "errors.world-not-found", Map.of());
                    return true;
                }
            }
        } else {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.world-not-found", Map.of());
                else sender.sendMessage("World not found");
                return true;
            }
            target = resolve(sender, args[2]);
            if (target == null) return true;
            worldWasSpecified = true;
        }

        applyPlayerWeather(sender, target, type, world, worldWasSpecified);
        return true;
    }

    private void sendError(CommandSender sender, String label, boolean hasOthers, String key) {
        if (sender instanceof Player p) {
            String usage = plugin.commandConfig().getUsage("pweather", hasOthers ? "others" : null, label);
            MessageUtil.send(p, "errors." + key, Map.of("usage", usage));
        } else {
            sender.sendMessage("Usage: /" + label + " <type> <world> <player>");
        }
    }

    private void applyPlayerWeather(CommandSender sender, OfflinePlayer target, String type, World world, boolean isWorldSpecific) {
        String typeDisplay = String.valueOf(plugin.lang().get("utilities.weather.placeholders." + type));
        UUID uuid = target.getUniqueId();
        String worldName = world != null ? world.getName() : "Unknown";

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
            if (uuid.equals(p.getUniqueId())) {
                String key = "utilities.weather." + (isWorldSpecific ? "player-set-in" : "player-set");
                MessageUtil.send(p, key, Map.of("type", typeDisplay, "world", worldName));
            } else {
                String key = isWorldSpecific ? "utilities.weather.player-set-in-for" : "utilities.weather.player-set-for";
                MessageUtil.send(p, key, Map.of("type", typeDisplay, "player", targetName, "world", worldName));

                if (target.isOnline() && target.getPlayer() != null) {
                    String targetKey = isWorldSpecific ? "utilities.weather.player-set-in-by" : "utilities.weather.player-set-by";
                    MessageUtil.send(target.getPlayer(), targetKey, Map.of("type", typeDisplay, "player", senderName, "world", worldName));
                }
            }
        } else {
            sender.sendMessage("Set " + type + " weather for " + targetName + (isWorldSpecific ? " in " + worldName : ""));
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found");
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();
        boolean hasOthers = sender.hasPermission(PERMISSION_OTHERS);

        if (args.length == 1) {
            return Stream.of(
                    plugin.commandConfig().getSelector("pweather", "clear"),
                    plugin.commandConfig().getSelector("pweather", "thunder"),
                    plugin.commandConfig().getSelector("pweather", "reset")
            ).filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2) {
            Stream<String> worlds = Bukkit.getWorlds().stream().map(World::getName);
            if (hasOthers) {
                Stream<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName);
                return Stream.concat(worlds, players).filter(s -> s.toLowerCase().startsWith(input)).limit(20).toList();
            }
            return worlds.filter(s -> s.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 3 && hasOthers) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .limit(20).toList();
        }

        return Collections.emptyList();
    }
}