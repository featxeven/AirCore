package com.ftxeven.aircore.module.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TpOfflineCommand implements TabExecutor {

    private final AirCore plugin;

    public TpOfflineCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.tpoffline")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tpoffline"));
            return true;
        }

        if (args.length != 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("tpoffline", label)));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return true;

        // Online case
        if (resolved.isOnline()) {
            Player targetOnline = resolved.getPlayer();
            if (targetOnline != null) {
                plugin.core().teleports().teleport(player, targetOnline.getLocation());
                MessageUtil.send(player, "teleport.direct.to-player",
                        Map.of("player", targetOnline.getName()));
                return true;
            }
        }

        // Offline case
        Location loc = plugin.database().records().getLocation(resolved.getUniqueId());
        if (loc == null) {
            String displayName = resolved.getName() != null ? resolved.getName() : args[0];
            MessageUtil.send(player, "teleport.errors.location-not-found",
                    Map.of("player", displayName));
            return true;
        }

        plugin.core().teleports().teleport(player, loc);

        String displayName = resolved.getName() != null ? resolved.getName() : args[0];
        MessageUtil.send(player, "teleport.direct.to-player-last",
                Map.of("player", displayName));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String[] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
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
