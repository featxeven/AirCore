package com.ftxeven.aircore.module.core.home.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.home.HomeManager;
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
import java.util.stream.Stream;

public final class DelHomeCommand implements TabExecutor {

    private final AirCore plugin;
    private final HomeManager manager;

    public DelHomeCommand(AirCore plugin, HomeManager manager) {
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

        if (!player.hasPermission("aircore.command.delhome")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.delhome"));
            return true;
        }

        // /delhome @p
        if (args.length > 0 && args[0].equalsIgnoreCase("@p")) {
            if (!player.hasPermission("aircore.command.delhome.others")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.delhome.others"));
                return true;
            }

            if (args.length < 2) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("delhome", "others", label)));
                return true;
            }

            OfflinePlayer target = resolve(player, args[1]);
            if (target == null) {
                return true;
            }

            if (args.length < 3) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("delhome", "others", label)));
                return true;
            }

            String homeName = args[2].toLowerCase();

            var homes = manager.homes().getHomes(target.getUniqueId());
            if (homes.isEmpty()) {
                var loaded = plugin.database().homes().load(target.getUniqueId());
                manager.homes().loadFromDatabase(target.getUniqueId(), loaded);
                homes = manager.homes().getHomes(target.getUniqueId());
            }

            if (!homes.containsKey(homeName)) {
                String displayName = target.getName() != null ? target.getName() : args[1];
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    MessageUtil.send(player, "homes.errors.not-found", Map.of("name", homeName));
                } else {
                    MessageUtil.send(player, "homes.errors.not-found-for",
                            Map.of("player", displayName, "name", homeName));
                }
                return true;
            }

            manager.homes().deleteHome(target.getUniqueId(), homeName);

            if (target.getUniqueId().equals(player.getUniqueId())) {
                // self-delete via @p
                MessageUtil.send(player, "homes.management.deleted", Map.of("name", homeName));
            } else {
                String displayName = target.getName() != null ? target.getName() : args[1];
                MessageUtil.send(player, "homes.management.deleted-for",
                        Map.of("player", displayName, "name", homeName));

                if (target.isOnline()) {
                    Player targetOnline = target.getPlayer();
                    MessageUtil.send(targetOnline, "homes.management.deleted-by",
                            Map.of("player", player.getName(), "name", homeName));
                }
            }
            return true;
        }

        // /delhome <homename>
        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("delhome", label)));
            return true;
        }

        String homeName = args[0].toLowerCase();

        var homes = manager.homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            var loaded = plugin.database().homes().load(player.getUniqueId());
            manager.homes().loadFromDatabase(player.getUniqueId(), loaded);
            homes = manager.homes().getHomes(player.getUniqueId());
        }

        if (!homes.containsKey(homeName)) {
            MessageUtil.send(player, "homes.errors.not-found", Map.of("name", homeName));
            return true;
        }

        manager.homes().deleteHome(player.getUniqueId(), homeName);
        MessageUtil.send(player, "homes.management.deleted", Map.of("name", homeName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        // first arg
        if (args.length == 1) {
            Stream<String> names = manager.homes()
                    .getHomes(player.getUniqueId())
                    .keySet()
                    .stream();

            if (player.hasPermission("aircore.command.delhome.others")) {
                names = Stream.concat(names, Stream.of("@p"));
            }

            return names.filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        // second arg after @p
        if (args.length == 2 && args[0].equalsIgnoreCase("@p")
                && player.hasPermission("aircore.command.delhome.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        // third arg after @p <player>
        if (args.length == 3 && args[0].equalsIgnoreCase("@p")
                && player.hasPermission("aircore.command.delhome.others")) {
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