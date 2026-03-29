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

import java.util.*;

public final class TpaHereCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.tpahere";
    private static final String PERM_ALL = "aircore.command.tpahere.all";

    public TpaHereCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("tpahere", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            if (!player.hasPermission(PERM_ALL)) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
            handleTpaHereAll(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", targetArg));
            return true;
        }

        if (target.equals(player)) {
            MessageUtil.send(player, "teleport.requests.error-self", Map.of());
            return true;
        }

        if (canReceiveRequest(player, target, false)) {
            executeRequest(player, target);
            MessageUtil.send(player, "teleport.requests.tpahere-to", Map.of("player", target.getName()));
        }

        return true;
    }

    private void handleTpaHereAll(Player player) {
        List<Player> others = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .map(p -> (Player) p)
                .toList();

        if (others.isEmpty()) {
            MessageUtil.send(player, "errors.no-players-online", Map.of());
            return;
        }

        int sentCount = 0;
        for (Player target : others) {
            if (canReceiveRequest(player, target, true)) {
                executeRequest(player, target);
                sentCount++;
            }
        }

        if (sentCount > 0) {
            MessageUtil.send(player, "teleport.requests.tpahere-everyone", Map.of());
        } else {
            MessageUtil.send(player, "teleport.requests.error-no-valid-targets", Map.of());
        }
    }

    private boolean canReceiveRequest(Player player, Player target, boolean silent) {
        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (plugin.core().blocks().isBlocked(targetId, playerId)) {
            if (!silent) MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", target.getName()));
            return false;
        }

        if (!player.hasPermission("aircore.bypass.teleport.toggle") &&
                !plugin.core().toggles().isEnabled(targetId, ToggleService.Toggle.TELEPORT)) {
            if (!silent) MessageUtil.send(player, "teleport.requests.error-disabled", Map.of("player", target.getName()));
            return false;
        }

        int cooldownSeconds = plugin.config().teleportRequestCooldown();
        if (cooldownSeconds > 0 && plugin.teleport().cooldowns().isOnCooldown(playerId, targetId, cooldownSeconds)) {
            if (!silent) {
                long remaining = plugin.teleport().cooldowns().getRemaining(playerId, targetId, cooldownSeconds);
                MessageUtil.send(player, "teleport.requests.error-cooldown", Map.of("time", TimeUtil.formatSeconds(plugin, remaining)));
            }
            return false;
        }

        return true;
    }

    private void executeRequest(Player player, Player target) {
        int expireSeconds = plugin.config().teleportRequestExpireTime();
        long expiryTime = expireSeconds > 0 ? System.currentTimeMillis() + (expireSeconds * 1000L) : Long.MAX_VALUE;

        plugin.teleport().requests().addRequest(
                player.getUniqueId(), player.getName(),
                target.getUniqueId(), target.getName(),
                expiryTime, RequestService.RequestType.TPAHERE
        );

        plugin.teleport().cooldowns().mark(player.getUniqueId(), target.getUniqueId());
        MessageUtil.send(target, "teleport.requests.tpahere-from", Map.of("player", player.getName()));

        if (plugin.utility().afk().isAfk(target.getUniqueId())) {
            MessageUtil.send(player, "utilities.afk.interaction-notify", Map.of("player", target.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input))
                .limit(20)
                .forEach(suggestions::add);

        if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
            suggestions.add(selectorAll);
        }

        return suggestions;
    }
}