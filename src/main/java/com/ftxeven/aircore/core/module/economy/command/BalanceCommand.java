package com.ftxeven.aircore.core.module.economy.command;

import com.ftxeven.aircore.AirCore;
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

public final class BalanceCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.balance";
    private static final String PERM_OTHERS = "aircore.command.balance.others";

    public BalanceCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleBalance(sender, args[0]);
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);

        if (args.length == 0) {
            handleBalance(player, player.getName());
            return true;
        }

        if (!hasOthers || (plugin.config().errorOnExcessArgs() && args.length > 1)) {
            String usage = plugin.commandConfig().getUsage("balance", hasOthers ? "others" : null, label);
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleBalance(player, args[0]);
        return true;
    }

    private void handleBalance(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        double balance = plugin.economy().balances().getBalance(resolved.getUniqueId());
        String formatted = plugin.economy().formats().formatAmount(balance);
        String finalName = plugin.database().records().getRealName(targetName);

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
        if (args.length != 1) return Collections.emptyList();

        if (sender instanceof Player player && !player.hasPermission(PERM_OTHERS)) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found");
        }
        return null;
    }
}