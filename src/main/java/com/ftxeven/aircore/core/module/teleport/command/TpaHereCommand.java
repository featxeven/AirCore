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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TpaHereCommand implements TabExecutor {

    private final AirCore plugin;

    public TpaHereCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.tpahere")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.tpahere"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("tpahere", label)));
            return true;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase("@a")) {
            handleTpaHereAll(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", targetName));
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
        if (!player.hasPermission("aircore.command.tpahere.all")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.tpahere.all"));
            return;
        }

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        targets.remove(player);

        if (targets.isEmpty()) {
            MessageUtil.send(player, "errors.no-players-online", Map.of());
            return;
        }

        int sentCount = 0;
        for (Player target : targets) {
            if (canReceiveRequest(player, target, true)) {
                executeRequest(player, target);
                sentCount++;
            }
        }

        if (sentCount > 0) {
            MessageUtil.send(player, "teleport.requests.tpahere-everyone", Map.of());
        } else {
            MessageUtil.send(player, "teleport.requests.error-blocked", Map.of());
        }
    }

    private boolean canReceiveRequest(Player player, Player target, boolean silent) {
        if (plugin.core().blocks().isBlocked(target.getUniqueId(), player.getUniqueId())) {
            if (!silent) MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", target.getName()));
            return false;
        }

        boolean bypassToggle = player.hasPermission("aircore.bypass.teleport.toggle");
        if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.TELEPORT)) {
            if (!silent) MessageUtil.send(player, "teleport.requests.error-disabled", Map.of("player", target.getName()));
            return false;
        }

        int cooldownSeconds = plugin.config().teleportRequestCooldown();
        if (cooldownSeconds > 0 && plugin.teleport().cooldowns().isOnCooldown(player.getUniqueId(), target.getUniqueId(), cooldownSeconds)) {
            if (!silent) {
                long remaining = plugin.teleport().cooldowns().getRemaining(player.getUniqueId(), target.getUniqueId(), cooldownSeconds);
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
            MessageUtil.send(player, "errors.afk-interaction-notify", Map.of("player", target.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();

        if (player.hasPermission("aircore.command.tpahere.all") && "@a".startsWith(input)) {
            completions.add("@a");
        }

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .forEach(completions::add);

        return completions;
    }
}