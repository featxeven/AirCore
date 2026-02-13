package com.ftxeven.aircore.core.home.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.home.HomeManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public final class HomeCommand implements TabExecutor {

    private final AirCore plugin;
    private final HomeManager manager;

    public HomeCommand(AirCore plugin, HomeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.home")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.home"));
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.home.others");
        boolean isTargetingOthers = args.length > 0 && args[0].equalsIgnoreCase("@p");

        if (isTargetingOthers) {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.home.others"));
                return true;
            }

            if (args.length < 3) {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("home", "others", label)));
                return true;
            }

            if (plugin.config().errorOnExcessArgs() && args.length > 3) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("home", "others", label)));
                return true;
            }

            OfflinePlayer target = resolve(player, args[1]);
            if (target == null) return true;

            handleTeleport(player, target, args[2]);
            return true;
        }

        if (args.length > 1) {
            if (plugin.config().errorOnExcessArgs()) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("home", label)));
                return true;
            }
        }

        var homes = manager.homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.scheduler().runAsync(() -> {
                var loaded = plugin.database().homes().load(player.getUniqueId());
                manager.homes().loadFromDatabase(player.getUniqueId(), loaded);
                var reloadedHomes = manager.homes().getHomes(player.getUniqueId());

                plugin.scheduler().runEntityTask(player, () -> processSelfTeleport(player, reloadedHomes, args, label));
            });
            return true;
        }

        processSelfTeleport(player, homes, args, label);
        return true;
    }

    private void processSelfTeleport(Player player, Map<String, Location> homes, String[] args, String label) {
        if (homes.isEmpty()) {
            MessageUtil.send(player, "homes.errors.none-yet", Map.of());
            return;
        }

        String homeName;
        if (args.length == 0) {
            if (homes.size() == 1) {
                homeName = homes.keySet().iterator().next();
            } else {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("home", label)));
                return;
            }
        } else {
            homeName = args[0].toLowerCase();
        }

        handleTeleport(player, player, homeName);
    }

    private void handleTeleport(Player player, OfflinePlayer target, String homeName) {
        UUID uuid = target.getUniqueId();
        String nameLower = homeName.toLowerCase();

        plugin.scheduler().runAsync(() -> {
            var homes = manager.homes().getHomes(uuid);
            if (homes.isEmpty()) {
                var loaded = plugin.database().homes().load(uuid);
                manager.homes().loadFromDatabase(uuid, loaded);
                homes = manager.homes().getHomes(uuid);
            }

            final Map<String, Location> finalHomes = homes;
            plugin.scheduler().runEntityTask(player, () -> {
                if (!finalHomes.containsKey(nameLower)) {
                    if (uuid.equals(player.getUniqueId())) {
                        MessageUtil.send(player, "homes.errors.not-found", Map.of("name", homeName));
                    } else {
                        String displayName = target.getName() != null ? target.getName() : uuid.toString();
                        MessageUtil.send(player, "homes.errors.not-found-for", Map.of("player", displayName, "name", homeName));
                    }
                    return;
                }

                Location loc = finalHomes.get(nameLower);

                if (uuid.equals(player.getUniqueId())) {
                    plugin.core().teleports().startCountdown(player, player, () -> {
                        plugin.core().teleports().teleport(player, loc);
                        MessageUtil.send(player, "homes.teleport.success", Map.of("name", homeName));
                    }, reason -> MessageUtil.send(player, "homes.teleport.cancelled", Map.of("name", homeName)));
                } else {
                    plugin.core().teleports().teleport(player, loc);
                    String displayName = target.getName() != null ? target.getName() : uuid.toString();
                    MessageUtil.send(player, "homes.teleport.other", Map.of("player", displayName, "name", homeName));
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            Stream<String> homes = manager.homes().getHomes(player.getUniqueId()).keySet().stream();
            if (player.hasPermission("aircore.command.home.others")) {
                homes = Stream.concat(homes, Stream.of("@p"));
            }
            return homes.filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("@p") && player.hasPermission("aircore.command.home.others")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("@p") && player.hasPermission("aircore.command.home.others")) {
            return getHomeCompletions(args[1], input);
        }

        return Collections.emptyList();
    }

    private List<String> getHomeCompletions(String targetName, String input) {
        UUID id = plugin.getNameCache().get(targetName.toLowerCase(Locale.ROOT));
        if (id == null) return Collections.emptyList();
        return manager.homes().getHomes(id).keySet().stream().filter(n -> n.toLowerCase().startsWith(input)).toList();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}