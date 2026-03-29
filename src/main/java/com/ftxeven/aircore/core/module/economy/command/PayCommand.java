package com.ftxeven.aircore.core.module.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
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

public final class PayCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.pay";
    private static final String BYPASS_TOGGLE = "aircore.bypass.pay.toggle";

    public PayCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("pay", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.commandConfig().getUsage("pay", label)));
            return true;
        }

        OfflinePlayer target = resolve(player, args[0]);
        if (target == null) return true;

        UUID targetId = target.getUniqueId();
        UUID senderId = player.getUniqueId();

        if (targetId.equals(senderId) && !plugin.config().economyAllowSelfPay()) {
            MessageUtil.send(player, "economy.payments.error-self", Map.of());
            return true;
        }

        String realTargetName = plugin.database().records().getRealName(args[0]);

        if (plugin.core().blocks().isBlocked(targetId, senderId)) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", realTargetName));
            return true;
        }

        Double parsed = plugin.economy().formats().parseAmount(args[1]);
        if (parsed == null || parsed <= 0) {
            MessageUtil.send(player, "errors.invalid-amount", Map.of());
            return true;
        }
        double amount = parsed;

        double minPay = plugin.config().economyMinPayAmount();
        double maxPay = plugin.config().economyMaxPayAmount();

        if (minPay > 0 && amount < minPay) {
            MessageUtil.send(player, "economy.payments.error-min", Map.of("amount", plugin.economy().formats().formatAmount(minPay)));
            return true;
        }
        if (maxPay >= 0 && amount > maxPay) {
            MessageUtil.send(player, "economy.payments.error-max", Map.of("amount", plugin.economy().formats().formatAmount(maxPay)));
            return true;
        }

        double senderBalance = plugin.economy().balances().getBalance(senderId);
        if (senderBalance < amount) {
            MessageUtil.send(player, "economy.payments.error-insufficient", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
            return true;
        }

        double maxBalance = plugin.config().economyMaxBalance();
        if (maxBalance >= 0) {
            double targetBalance = plugin.economy().balances().getBalance(targetId);
            if (targetBalance + amount > maxBalance) {
                MessageUtil.send(player, "economy.payments.error-exceed",
                        Map.of("player", realTargetName, "amount", plugin.economy().formats().formatAmount(maxBalance)));
                return true;
            }
        }

        if (!plugin.core().toggles().isEnabled(targetId, ToggleService.Toggle.PAY) && !player.hasPermission(BYPASS_TOGGLE)) {
            MessageUtil.send(player, "economy.payments.error-disabled", Map.of("player", realTargetName));
            return true;
        }

        plugin.economy().transactions().withdraw(senderId, amount);
        plugin.economy().transactions().deposit(targetId, amount);

        String formattedAmount = plugin.economy().formats().formatAmount(amount);
        MessageUtil.send(player, "economy.payments.send", Map.of("player", realTargetName, "amount", formattedAmount));

        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtil.send(target.getPlayer(), "economy.payments.receive",
                    Map.of("player", player.getName(), "amount", formattedAmount));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !(sender instanceof Player player) || !player.hasPermission(PERM_BASE)) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}