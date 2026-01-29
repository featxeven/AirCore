package com.ftxeven.aircore.module.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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

        // Console
        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.lang().get("errors.player-not-found"));
                return true;
            }

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PAY);

            sender.sendMessage("Payments for " + target.getName() + " -> " + (newState ? "enabled" : "disabled"));

            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target,
                        newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        // Player
        if (!player.hasPermission("aircore.command.paytoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.paytoggle"));
            return true;
        }

        // Self toggle
        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PAY);
            MessageUtil.send(player,
                    newState ? "economy.payments.toggles.enabled" : "economy.payments.toggles.disabled",
                    Map.of());
            return true;
        }

        // Toggle another player
        if (!player.hasPermission("aircore.command.paytoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.paytoggle.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PAY);

        MessageUtil.send(player,
                newState ? "economy.payments.toggles.enabled-for" : "economy.payments.toggles.disabled-for",
                Map.of("player", target.getName()));

        MessageUtil.send(target,
                newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                Map.of("player", player.getName()));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.paytoggle.others")) {
                return List.of();
            }
        }

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}
