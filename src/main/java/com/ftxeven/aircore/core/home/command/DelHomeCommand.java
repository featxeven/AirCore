package com.ftxeven.aircore.core.home.command;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class DelHomeCommand implements TabExecutor {

    private final AirCore plugin;

    public DelHomeCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <player> <homename>");
                return true;
            }

            OfflinePlayer target = resolve(sender, args[0]);
            if (target == null) return true;

            handleDelete(sender, target, args[1], plugin.lang().get("general.console-name"), true);
            return true;
        }

        if (!player.hasPermission("aircore.command.delhome")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.delhome"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("@p")) {
            if (!player.hasPermission("aircore.command.delhome.others")) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.delhome.others"));
                return true;
            }

            if (args.length < 3) {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("delhome", "others", label)));
                return true;
            }

            if (plugin.config().errorOnExcessArgs() && args.length > 3) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("delhome", "others", label)));
                return true;
            }

            OfflinePlayer target = resolve(player, args[1]);
            if (target == null) return true;

            handleDelete(player, target, args[2], player.getName(), false);
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("delhome", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("delhome", label)));
            return true;
        }

        handleDelete(player, player, args[0], player.getName(), false);
        return true;
    }

    private void handleDelete(CommandSender sender, OfflinePlayer target, String homeName, String actorName, boolean isConsole) {
        UUID uuid = target.getUniqueId();
        String nameLower = homeName.toLowerCase();

        var homes = plugin.home().homes().getHomes(uuid);
        if (homes.isEmpty()) {
            var loaded = plugin.database().homes().load(uuid);
            plugin.home().homes().loadFromDatabase(uuid, loaded);
            homes = plugin.home().homes().getHomes(uuid);
        }

        if (!homes.containsKey(nameLower)) {
            if (sender instanceof Player p) {
                if (uuid.equals(p.getUniqueId())) {
                    MessageUtil.send(p, "homes.errors.not-found", Map.of("name", homeName));
                } else {
                    String displayName = target.getName() != null ? target.getName() : uuid.toString();
                    MessageUtil.send(p, "homes.errors.not-found-for", Map.of("player", displayName, "name", homeName));
                }
            } else {
                sender.sendMessage("Home '" + homeName + "' not found for " + (target.getName() != null ? target.getName() : uuid));
            }
            return;
        }

        plugin.home().homes().deleteHome(uuid, nameLower);

        if (sender instanceof Player p) {
            if (uuid.equals(p.getUniqueId())) {
                MessageUtil.send(p, "homes.management.deleted", Map.of("name", homeName));
            } else {
                String displayName = target.getName() != null ? target.getName() : uuid.toString();
                MessageUtil.send(p, "homes.management.deleted-for", Map.of("player", displayName, "name", homeName));
            }
        } else {
            sender.sendMessage("Deleted home '" + homeName + "' for " + (target.getName() != null ? target.getName() : uuid));
        }

        if (target.isOnline() && target.getPlayer() != null) {
            Player onlineTarget = target.getPlayer();
            if (sender instanceof Player p && onlineTarget.equals(p)) return;

            if (!isConsole || plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(onlineTarget, "homes.management.deleted-by", Map.of("player", actorName, "name", homeName));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String input = args[args.length - 1].toLowerCase();

        if (!(sender instanceof Player player)) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(input)).toList();
            }
            if (args.length == 2) {
                return getHomeCompletions(args[0], input);
            }
            return List.of();
        }

        if (args.length == 1) {
            Stream<String> homes = plugin.home().homes().getHomes(player.getUniqueId()).keySet().stream();
            if (player.hasPermission("aircore.command.delhome.others")) {
                homes = Stream.concat(homes, Stream.of("@p"));
            }
            return homes.filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("@p") && player.hasPermission("aircore.command.delhome.others")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("@p") && player.hasPermission("aircore.command.delhome.others")) {
            return getHomeCompletions(args[1], input);
        }

        return List.of();
    }

    private List<String> getHomeCompletions(String targetName, String input) {
        UUID id = plugin.getNameCache().get(targetName.toLowerCase(Locale.ROOT));
        if (id == null) return List.of();
        return plugin.home().homes().getHomes(id).keySet().stream().filter(n -> n.toLowerCase().startsWith(input)).toList();
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found in database.");
        }
        return null;
    }
}