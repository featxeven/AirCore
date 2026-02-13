package com.ftxeven.aircore.core.utility.command;

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

    public BlockCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (!player.hasPermission("aircore.command.block")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.block"));
            return true;
        }

        if (args.length == 0) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("block", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("block", label)));
            return true;
        }

        String targetName = args[0];

        plugin.scheduler().runAsync(() -> {
            OfflinePlayer resolved = resolveOffline(targetName);

            if (resolved == null) {
                plugin.scheduler().runTask(() -> MessageUtil.send(player, "errors.player-never-joined", Map.of()));
                return;
            }

            if (resolved.getUniqueId().equals(player.getUniqueId())) {
                plugin.scheduler().runTask(() -> MessageUtil.send(player, "utilities.blocking.error-self", Map.of()));
                return;
            }

            boolean bypass = hasBypassPermission(resolved);
            String displayName = resolved.getName() != null ? resolved.getName() : targetName;

            plugin.scheduler().runTask(() -> {
                if (!player.isOnline()) return;

                if (bypass) {
                    MessageUtil.send(player, "utilities.blocking.error-cannot", Map.of("player", displayName));
                    return;
                }

                executeBlock(player, resolved.getUniqueId(), displayName);
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

        int limit = plugin.core().blocks().getBlockLimit(playerId);
        int current = plugin.core().blocks().getBlocked(playerId).size();

        if (current >= limit) {
            MessageUtil.send(executor, "utilities.blocking.limit", Map.of("limit", String.valueOf(limit)));
            return;
        }

        plugin.core().blocks().block(playerId, targetId);
        plugin.scheduler().runAsync(() -> plugin.database().blocks().add(playerId, targetId));

        MessageUtil.send(executor, "utilities.blocking.added", Map.of("player", displayName));
    }

    private boolean hasBypassPermission(OfflinePlayer target) {
        if (target.isOp()) return true;

        if (target instanceof Player online) {
            return online.hasPermission("aircore.bypass.block");
        }

        var registration = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null) return false;

        LuckPerms api = registration.getProvider();
        User user = api.getUserManager().loadUser(target.getUniqueId()).join();

        return user != null && user.getCachedData().getPermissionData()
                .checkPermission("aircore.bypass.block").asBoolean();
    }

    private OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        return (cached != null) ? Bukkit.getOfflinePlayer(cached) : null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission("aircore.command.block")) return Collections.emptyList();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}