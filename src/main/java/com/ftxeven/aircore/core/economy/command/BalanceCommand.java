package com.ftxeven.aircore.core.economy.command;

import com.ftxeven.aircore.AirCore;
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

public final class BalanceCommand implements TabExecutor {

    private final AirCore plugin;
    private final EconomyManager manager;

    public BalanceCommand(AirCore plugin, EconomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleBalance(sender, args[0]);
            return true;
        }

        if (!player.hasPermission("aircore.command.balance")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.balance"));
            return true;
        }

        if (args.length == 0) {
            handleBalance(player, player.getName());
            return true;
        }

        if (!player.hasPermission("aircore.command.balance.others")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.balance.others"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("balance", "others", label)));
            return true;
        }

        handleBalance(player, args[0]);
        return true;
    }

    private void handleBalance(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        double balance = manager.balances().getBalance(resolved.getUniqueId());
        String formatted = manager.formats().formatAmount(balance);
        String finalName = resolved.getName() != null ? resolved.getName() : targetName;

        if (sender instanceof Player p) {
            if (resolved.getUniqueId().equals(p.getUniqueId())) {
                MessageUtil.send(p, "economy.balance.self", Map.of("balance", formatted));
            } else {
                MessageUtil.send(p, "economy.balance.player", Map.of("player", finalName, "balance", formatted));
            }
        } else {
            sender.sendMessage(finalName + "'s balance: " + formatted);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        if (sender instanceof Player player && !player.hasPermission("aircore.command.balance.others")) {
            return List.of();
        }

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found in database.");
        }
        return null;
    }
}