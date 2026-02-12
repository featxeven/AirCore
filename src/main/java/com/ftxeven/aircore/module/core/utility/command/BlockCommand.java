package com.ftxeven.aircore.module.core.utility.command;

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
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.block"));
            return true;
        }

        if (args.length != 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("block", label)));
            return true;
        }

        UUID playerId = player.getUniqueId();
        String targetName = args[0];

        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return true;

        UUID targetId = resolved.getUniqueId();
        String displayName = resolved.getName() != null ? resolved.getName() : targetName;

        if (targetId.equals(playerId)) {
            MessageUtil.send(player, "chat.blocking.error-self", Map.of());
            return true;
        }

        if (resolved instanceof Player targetOnline) {
            if (targetOnline.hasPermission("aircore.bypass.block")) {
                MessageUtil.send(player, "chat.blocking.error-cannot", Map.of("player", displayName));
                return true;
            }
        } else {
            if (hasLuckPermsPermission(targetId)) {
                MessageUtil.send(player, "chat.blocking.error-cannot", Map.of("player", displayName));
                return true;
            }
        }

        blockPlayer(playerId, targetId, displayName);
        return true;
    }

    private void blockPlayer(UUID playerId, UUID targetId, String displayName) {
        Player executor = Bukkit.getPlayer(playerId);

        if (plugin.core().blocks().isBlocked(playerId, targetId)) {
            MessageUtil.send(executor, "chat.blocking.already", Map.of("player", displayName));
            return;
        }

        int limit = plugin.core().blocks().getBlockLimit(playerId);
        int current = plugin.core().blocks().getBlocked(playerId).size();
        if (current >= limit) {
            MessageUtil.send(executor, "chat.blocking.limit",
                    Map.of("limit", String.valueOf(limit)));
            return;
        }

        plugin.core().blocks().block(playerId, targetId);
        plugin.scheduler().runAsync(() ->
                plugin.database().blocks().add(playerId, targetId)
        );

        MessageUtil.send(executor, "chat.blocking.added", Map.of("player", displayName));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("aircore.command.block")) return List.of();
        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private boolean hasLuckPermsPermission(UUID uuid) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.isOp()) return true;

            var registration = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (registration == null) return false;

            LuckPerms api = registration.getProvider();
            User user = api.getUserManager().loadUser(uuid).join();
            if (user == null) return false;

            return user.getCachedData().getPermissionData()
                    .checkPermission("aircore.bypass.block").asBoolean();
        } catch (Exception e) {
            return false;
        }
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