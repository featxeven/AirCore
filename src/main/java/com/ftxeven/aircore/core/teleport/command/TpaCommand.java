package com.ftxeven.aircore.core.teleport.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.core.teleport.service.RequestService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class TpaCommand implements TabExecutor {

    private final AirCore plugin;

    public TpaCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.tpa")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tpa"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("tpa", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("tpa", label)));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        if (target.equals(player)) {
            MessageUtil.send(player, "teleport.requests.error-self", Map.of());
            return true;
        }

        if (plugin.core().blocks().isBlocked(target.getUniqueId(), player.getUniqueId())) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by",
                    Map.of("player", target.getName()));
            return true;
        }

        boolean bypassToggle = player.hasPermission("aircore.bypass.teleport.toggle");
        if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.TELEPORT)) {
            MessageUtil.send(player, "teleport.requests.error-disabled",
                    Map.of("player", target.getName()));
            return true;
        }

        int cooldownSeconds = plugin.config().teleportRequestCooldown();
        if (cooldownSeconds > 0 &&
                plugin.teleport().cooldowns().isOnCooldown(player.getUniqueId(), target.getUniqueId(), cooldownSeconds)) {
            long remaining = plugin.teleport().cooldowns().getRemaining(player.getUniqueId(), target.getUniqueId(), cooldownSeconds);
            MessageUtil.send(player, "teleport.requests.error-cooldown",
                    Map.of("time", TimeUtil.formatSeconds(plugin, remaining)));
            return true;
        }

        int expireSeconds = plugin.config().teleportRequestExpireTime();
        long expiryTime = expireSeconds > 0
                ? System.currentTimeMillis() + (expireSeconds * 1000L)
                : Long.MAX_VALUE;

        plugin.teleport().requests().addRequest(
                player.getUniqueId(), player.getName(),
                target.getUniqueId(), target.getName(),
                expiryTime, RequestService.RequestType.TPA
        );

        plugin.teleport().cooldowns().mark(player.getUniqueId(), target.getUniqueId());

        MessageUtil.send(player, "teleport.requests.tpa-to", Map.of("player", target.getName()));
        MessageUtil.send(target, "teleport.requests.tpa-from", Map.of("player", player.getName()));

        if (plugin.utility().afk().isAfk(target.getUniqueId())) {
            MessageUtil.send(player, "errors.afk-interaction-notify", Map.of("player", target.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player) || args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}