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

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.home"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("@p")) {
            if (!player.hasPermission("aircore.command.home.others")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.home.others"));
                return true;
            }

            if (args.length < 2) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("home", "others", label)));
                return true;
            }

            OfflinePlayer target = resolve(player, args[1]);
            if (target == null) return true;

            if (args.length < 3) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("home", "others", label)));
                return true;
            }

            String homeName = args[2].toLowerCase();
            UUID targetUuid = target.getUniqueId();
            String displayName = target.getName() != null ? target.getName() : args[1];

            plugin.scheduler().runAsync(() -> {
                var homes = manager.homes().getHomes(targetUuid);
                if (homes.isEmpty()) {
                    var loaded = plugin.database().homes().load(targetUuid);
                    manager.homes().loadFromDatabase(targetUuid, loaded);
                    homes = manager.homes().getHomes(targetUuid);
                }

                if (!homes.containsKey(homeName)) {
                    MessageUtil.send(player, "homes.errors.not-found-for", Map.of("player", displayName));
                    return;
                }

                Location loc = homes.get(homeName);

                plugin.core().teleports().teleport(player, loc);

                if (targetUuid.equals(player.getUniqueId())) {
                    MessageUtil.send(player, "homes.teleport.success", Map.of("name", homeName));
                } else {
                    MessageUtil.send(player, "homes.teleport.other",
                            Map.of("player", displayName, "name", homeName));
                }
            });

            return true;
        }

        var homes = manager.homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.scheduler().runAsync(() -> {
                var loaded = plugin.database().homes().load(player.getUniqueId());
                manager.homes().loadFromDatabase(player.getUniqueId(), loaded);
                var reloadedHomes = manager.homes().getHomes(player.getUniqueId());

                if (reloadedHomes.isEmpty()) {
                    MessageUtil.send(player, "homes.errors.none-yet", Map.of());
                    return;
                }

                handleHomeCommand(player, reloadedHomes, args, label);
            });
            return true;
        }

        handleHomeCommand(player, homes, args, label);
        return true;
    }

    private void handleHomeCommand(Player player, Map<String, Location> homes, String[] args, String label) {
        if (homes.isEmpty()) {
            MessageUtil.send(player, "homes.errors.none-yet", Map.of());
            return;
        }

        if (homes.size() == 1 && args.length == 0) {
            String homeName = homes.keySet().iterator().next();
            Location loc = homes.get(homeName);

            plugin.core().teleports().startCountdown(
                    player,
                    player,
                    () -> {
                        plugin.core().teleports().teleport(player, loc);
                        MessageUtil.send(player, "homes.teleport.success", Map.of("name", homeName));
                    },
                    cancelReason -> MessageUtil.send(player, "homes.teleport.cancelled", Map.of("name", homeName))
            );
            return;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("home", label)));
            return;
        }

        String homeName = args[0].toLowerCase();
        Location loc = homes.get(homeName);
        if (loc == null) {
            MessageUtil.send(player, "homes.errors.not-found", Map.of("name", homeName));
            return;
        }

        plugin.core().teleports().startCountdown(
                player,
                player,
                () -> {
                    plugin.core().teleports().teleport(player, loc);
                    MessageUtil.send(player, "homes.teleport.success", Map.of("name", homeName));
                },
                cancelReason -> MessageUtil.send(player, "homes.teleport.cancelled", Map.of("name", homeName))
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            Stream<String> names = manager.homes()
                    .getHomes(player.getUniqueId())
                    .keySet()
                    .stream();

            if (player.hasPermission("aircore.command.home.others")) {
                names = Stream.concat(names, Stream.of("@p"));
            }

            return names.filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("@p")
                && player.hasPermission("aircore.command.home.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("@p")
                && player.hasPermission("aircore.command.home.others")) {
            UUID targetId = plugin.getNameCache().get(args[1].toLowerCase());
            if (targetId == null) return List.of();

            var homes = manager.homes().getHomes(targetId);
            if (homes.isEmpty()) {
                var loaded = plugin.database().homes().load(targetId);
                manager.homes().loadFromDatabase(targetId, loaded);
                homes = manager.homes().getHomes(targetId);
            }

            return homes.keySet().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) {
            return Bukkit.getOfflinePlayer(cached);
        }

        MessageUtil.send(sender, "errors.player-never-joined", Map.of("player", name));
        return null;
    }
}