package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class UnblockCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.unblock";

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

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length == 0) {
            sendError(player, label, "incorrect-usage");
            return true;
        }

        if (args.length > 1) {
            sendError(player, label, "too-many-arguments");
            return true;
        }

        UUID playerId = player.getUniqueId();
        String targetName = args[0];

        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return true;

        UUID targetId = resolved.getUniqueId();
        String realName = plugin.database().records().getRealName(targetName);

        if (!plugin.core().blocks().isBlocked(playerId, targetId)) {
            MessageUtil.send(player, "utilities.blocking.not-blocked", Map.of("player", realName));
            return true;
        }

        plugin.api().blocks().unblock(playerId, targetId);
        MessageUtil.send(player, "utilities.blocking.removed", Map.of("player", realName));
        return true;
    }

    private void sendError(Player player, String label, String key) {
        String usage = plugin.commandConfig().getUsage("unblock", null, label);
        MessageUtil.send(player, "errors." + key, Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            UUID playerId = player.getUniqueId();
            String input = args[0].toLowerCase();

            return plugin.core().blocks().getBlocked(playerId).stream()
                    .map(uuid -> plugin.database().records().getName(uuid))
                    .filter(Objects::nonNull)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }
        return Collections.emptyList();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) {
            return Bukkit.getOfflinePlayer(uuid);
        }

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}