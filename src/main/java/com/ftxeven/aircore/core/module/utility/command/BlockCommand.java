package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
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

public final class BlockCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.block";
    private static final String BYPASS_PERM = "aircore.bypass.block";

    public BlockCommand(AirCore plugin) {
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

        String targetName = args[0];

        plugin.scheduler().runAsync(() -> {
            OfflinePlayer resolved = resolve(player, targetName);
            if (resolved == null) return;

            UUID targetId = resolved.getUniqueId();
            String realName = plugin.database().records().getRealName(targetName);

            if (targetId.equals(player.getUniqueId())) {
                plugin.scheduler().runTask(() -> MessageUtil.send(player, "utilities.blocking.error-self", Map.of()));
                return;
            }

            if (hasBypassPermission(resolved)) {
                plugin.scheduler().runTask(() -> MessageUtil.send(player, "utilities.blocking.error-cannot", Map.of("player", realName)));
                return;
            }

            plugin.scheduler().runTask(() -> {
                if (!player.isOnline()) return;
                executeBlock(player, targetId, realName);
            });
        });

        return true;
    }

    private void executeBlock(Player executor, UUID targetId, String displayName) {
        UUID playerId = executor.getUniqueId();

        if (plugin.core().blocks().isBlocked(playerId, targetId)) {
            MessageUtil.send(executor, "utilities.blocking.already", Map.of("player", displayName));
            return;
        }

        int limit = plugin.core().blocks().getLimit(playerId);
        int currentSize = plugin.core().blocks().getBlocked(playerId).size();

        if (currentSize >= limit) {
            MessageUtil.send(executor, "utilities.blocking.limit", Map.of("limit", String.valueOf(limit)));
            return;
        }

        plugin.api().blocks().block(playerId, targetId);
        MessageUtil.send(executor, "utilities.blocking.added", Map.of("player", displayName));
    }

    private void sendError(Player player, String label, String key) {
        String usage = plugin.commandConfig().getUsage("block", null, label);
        MessageUtil.send(player, "errors." + key, Map.of("usage", usage));
    }

    private boolean hasBypassPermission(OfflinePlayer target) {
        if (target.isOp()) return true;
        if (target instanceof Player online) return online.hasPermission(BYPASS_PERM);

        var registration = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null) return false;

        LuckPerms api = registration.getProvider();
        User user = api.getUserManager().loadUser(target.getUniqueId()).join();

        return user != null && user.getCachedData().getPermissionData()
                .checkPermission(BYPASS_PERM).asBoolean();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        plugin.scheduler().runTask(() -> MessageUtil.send(sender, "errors.player-never-joined", Map.of()));
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1 || !player.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}