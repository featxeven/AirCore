package com.ftxeven.aircore.module.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
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

        // Console execution
        if (!(sender instanceof Player player)) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /balance <player>");
                return true;
            }

            OfflinePlayer target = resolve(sender, args[0]);
            if (target == null) return true;

            double balance = manager.balances().getBalance(target.getUniqueId());
            String targetName = target.getName() != null ? target.getName() : args[0];
            sender.sendMessage(targetName + "'s balance: " + manager.formats().formatAmount(balance));
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.balance")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.balance"));
            return true;
        }

        // /balance > self
        if (args.length == 0) {
            double balance = manager.balances().getBalance(player.getUniqueId());
            MessageUtil.send(player, "economy.balance.self",
                    Map.of("balance", manager.formats().formatAmount(balance)));
            return true;
        }

        // /balance <player>
        if (!player.hasPermission("aircore.command.balance.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.balance.others"));
            return true;
        }

        OfflinePlayer resolved = resolve(player, args[0]);
        if (resolved == null) return true;

        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            double balance = manager.balances().getBalance(player.getUniqueId());
            MessageUtil.send(player, "economy.balance.self",
                    Map.of("balance", manager.formats().formatAmount(balance)));
            return true;
        }

        String targetName = resolved.getName() != null ? resolved.getName() : args[0];
        double balance = manager.balances().getBalance(resolved.getUniqueId());
        MessageUtil.send(player, "economy.balance.player",
                Map.of("player", targetName,
                        "balance", manager.formats().formatAmount(balance)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.balance")) return List.of();

        if (!player.hasPermission("aircore.command.balance.others")) return List.of();

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) {
            return Bukkit.getOfflinePlayer(cached);
        }

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of("player", name));
        } else {
            sender.sendMessage("Player not found");
        }
        return null;
    }
}
