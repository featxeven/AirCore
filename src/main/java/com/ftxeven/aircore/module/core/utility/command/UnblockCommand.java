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

public final class UnblockCommand implements TabExecutor {

    private final AirCore plugin;

    public UnblockCommand(AirCore plugin) {
        this.plugin = plugin;
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

        if (!player.hasPermission("aircore.command.unblock")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.unblock"));
            return true;
        }

        if (args.length != 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("unblock", label)));
            return true;
        }

        UUID playerId = player.getUniqueId();
        String targetName = args[0];

        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return true;

        UUID targetId = resolved.getUniqueId();
        String displayName = resolved.getName() != null ? resolved.getName() : targetName;

        if (!plugin.core().blocks().isBlocked(playerId, targetId)) {
            MessageUtil.send(player, "chat.blocking.not-blocked",
                    Map.of("player", displayName));
            return true;
        }

        plugin.core().blocks().unblock(playerId, targetId);
        plugin.scheduler().runAsync(() ->
                plugin.database().blocks().remove(playerId, targetId)
        );

        MessageUtil.send(player, "chat.blocking.removed",
                Map.of("player", displayName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("aircore.command.unblock")) return List.of();
        if (args.length != 1) return List.of();

        UUID playerId = player.getUniqueId();
        String input = args[0].toLowerCase();

        return plugin.core().blocks().getBlocked(playerId).stream()
                .map(uuid -> plugin.getServer().getOfflinePlayer(uuid).getName())
                .filter(name -> name != null && name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
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

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}