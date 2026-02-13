package com.ftxeven.aircore.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
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

public final class PayToggleCommand implements TabExecutor {

    private final AirCore plugin;

    public PayToggleCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            OfflinePlayer target = resolve(null, args[0]);
            if (target == null) return true;

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PAY);
            String targetName = target.getName() != null ? target.getName() : args[0];

            sender.sendMessage("Payments for " + targetName + " -> " + (newState ? "enabled" : "disabled"));

            if (target.isOnline() && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target.getPlayer(),
                        newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.paytoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.paytoggle"));
            return true;
        }

        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PAY);
            MessageUtil.send(player,
                    newState ? "economy.payments.toggles.enabled" : "economy.payments.toggles.disabled",
                    Map.of());
            return true;
        }

        OfflinePlayer target = resolve(player, args[0]);
        if (target == null) return true;

        if (target.getUniqueId().equals(player.getUniqueId())) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PAY);
            MessageUtil.send(player,
                    newState ? "economy.payments.toggles.enabled" : "economy.payments.toggles.disabled",
                    Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.paytoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.paytoggle.others"));
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PAY);
        String targetName = target.getName() != null ? target.getName() : args[0];

        MessageUtil.send(player,
                newState ? "economy.payments.toggles.enabled-for" : "economy.payments.toggles.disabled-for",
                Map.of("player", targetName));

        if (target.isOnline()) {
            MessageUtil.send(target.getPlayer(),
                    newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                    Map.of("player", player.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    if (sender.hasPermission("aircore.command.paytoggle.others")) return true;
                    return p.getName().equalsIgnoreCase(sender.getName());
                })
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

        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return Bukkit.getOfflinePlayer(cached);
        }

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else if (sender != null) {
            sender.sendMessage("Player not found");
        }
        return null;
    }
}