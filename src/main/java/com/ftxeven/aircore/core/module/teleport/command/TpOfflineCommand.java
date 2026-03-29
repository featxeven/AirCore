package com.ftxeven.aircore.core.module.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public final class TpOfflineCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.tpoffline";

    public TpOfflineCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("tpoffline", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        OfflinePlayer resolved = resolve(player, args[0]);
        if (resolved == null) return true;

        String displayName = plugin.database().records().getRealName(args[0]);
        Player targetOnline = resolved.getPlayer();

        if (targetOnline != null) {
            plugin.core().teleports().teleport(player, targetOnline.getLocation());
            MessageUtil.send(player, "teleport.direct.to-player", Map.of("player", displayName));
            return true;
        }

        Location loc = plugin.database().records().getLocation(resolved.getUniqueId());
        if (loc == null) {
            MessageUtil.send(player, "teleport.errors.location-not-found", Map.of("player", displayName));
            return true;
        }

        plugin.core().teleports().teleport(player, loc);
        MessageUtil.send(player, "teleport.direct.to-player-last", Map.of("player", displayName));

        return true;
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}