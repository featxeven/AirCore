package com.ftxeven.aircore.core.module.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.core.module.teleport.service.RequestService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TpaCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.tpa";

    public TpaCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("tpa", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (targetId.equals(playerId)) {
            MessageUtil.send(player, "teleport.requests.error-self", Map.of());
            return true;
        }

        if (plugin.core().blocks().isBlocked(targetId, playerId)) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", target.getName()));
            return true;
        }

        if (!player.hasPermission("aircore.bypass.teleport.toggle") &&
                !plugin.core().toggles().isEnabled(targetId, ToggleService.Toggle.TELEPORT)) {
            MessageUtil.send(player, "teleport.requests.error-disabled", Map.of("player", target.getName()));
            return true;
        }

        int cooldownSeconds = plugin.config().teleportRequestCooldown();
        if (cooldownSeconds > 0 && plugin.teleport().cooldowns().isOnCooldown(playerId, targetId, cooldownSeconds)) {
            long remaining = plugin.teleport().cooldowns().getRemaining(playerId, targetId, cooldownSeconds);
            MessageUtil.send(player, "teleport.requests.error-cooldown", Map.of("time", TimeUtil.formatSeconds(plugin, remaining)));
            return true;
        }

        int expireSeconds = plugin.config().teleportRequestExpireTime();
        long expiryTime = expireSeconds > 0 ? System.currentTimeMillis() + (expireSeconds * 1000L) : Long.MAX_VALUE;

        plugin.teleport().requests().addRequest(
                playerId, player.getName(),
                targetId, target.getName(),
                expiryTime, RequestService.RequestType.TPA
        );

        plugin.teleport().cooldowns().mark(playerId, targetId);

        MessageUtil.send(player, "teleport.requests.tpa-to", Map.of("player", target.getName()));
        MessageUtil.send(target, "teleport.requests.tpa-from", Map.of("player", player.getName()));

        if (plugin.utility().afk().isAfk(targetId)) {
            MessageUtil.send(player, "utilities.afk.interaction-notify", Map.of("player", target.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}