package com.ftxeven.aircore.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PayCommand implements TabExecutor {

    private final EconomyManager manager;
    private final AirCore plugin;

    public PayCommand(AirCore plugin, EconomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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

        if (!player.hasPermission("aircore.command.pay")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.pay"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("pay", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("pay", label)));
            return true;
        }

        String targetArg = args[0];
        OfflinePlayer target = resolve(player, targetArg);
        if (target == null) return true;

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "economy.payments.error-self", Map.of());
            return true;
        }

        UUID targetId = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : targetArg;

        if (plugin.core().blocks().isBlocked(targetId, player.getUniqueId())) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", targetName));
            return true;
        }

        Double parsed = manager.formats().parseAmount(args[1]);
        if (parsed == null || parsed <= 0) {
            MessageUtil.send(player, "errors.invalid-amount", Map.of());
            return true;
        }
        double amount = parsed;

        double minPay = plugin.config().economyMinPayAmount();
        double maxPay = plugin.config().economyMaxPayAmount();

        if (minPay > 0 && amount < minPay) {
            MessageUtil.send(player, "economy.payments.error-min", Map.of("amount", manager.formats().formatAmount(minPay)));
            return true;
        }
        if (maxPay >= 0 && amount > maxPay) {
            MessageUtil.send(player, "economy.payments.error-max", Map.of("amount", manager.formats().formatAmount(maxPay)));
            return true;
        }

        UUID senderId = player.getUniqueId();
        double senderBalance = manager.balances().getBalance(senderId);
        if (senderBalance < amount) {
            MessageUtil.send(player, "economy.payments.error-insufficient", Map.of("amount", manager.formats().formatAmount(amount)));
            return true;
        }

        double maxBalance = plugin.config().economyMaxBalance();
        if (maxBalance >= 0) {
            double targetBalance = manager.balances().getBalance(targetId);
            if (targetBalance + amount > maxBalance) {
                MessageUtil.send(player, "economy.payments.error-exceed",
                        Map.of("player", targetName, "amount", manager.formats().formatAmount(maxBalance)));
                return true;
            }
        }

        if (!plugin.core().toggles().isEnabled(targetId, ToggleService.Toggle.PAY)
                && !player.hasPermission("aircore.bypass.pay.toggle")) {
            MessageUtil.send(player, "economy.payments.error-disabled", Map.of("player", targetName));
            return true;
        }

        manager.transactions().withdraw(senderId, amount);
        manager.transactions().deposit(targetId, amount);

        MessageUtil.send(player, "economy.payments.send", Map.of("player", targetName, "amount", manager.formats().formatAmount(amount)));
        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtil.send(target.getPlayer(), "economy.payments.receive",
                    Map.of("player", player.getName(), "amount", manager.formats().formatAmount(amount)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("aircore.command.pay") || args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }

        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}