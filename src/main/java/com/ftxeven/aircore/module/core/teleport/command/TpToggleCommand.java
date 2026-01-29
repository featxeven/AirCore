package com.ftxeven.aircore.module.core.teleport.command;

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

public final class TpToggleCommand implements TabExecutor {

    private final AirCore plugin;

    public TpToggleCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console behavior
        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.TELEPORT);

            sender.sendMessage("Teleport toggle status for " + target.getName() + " -> "
                    + (newState ? "enabled" : "disabled"));

            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target,
                        newState ? "teleport.toggles.enabled-by" : "teleport.toggles.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        // Player behavior
        if (!player.hasPermission("aircore.command.tptoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tptoggle"));
            return true;
        }

        // Self toggle: /tptoggle
        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.TELEPORT);
            MessageUtil.send(player, newState ? "teleport.toggles.enabled" : "teleport.toggles.disabled", Map.of());
            return true;
        }

        // /tptoggle <player>
        Player target = Bukkit.getPlayerExact(args[0]);

        // Self-target
        if (target != null && target.equals(player)) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.TELEPORT);
            MessageUtil.send(player, newState ? "teleport.toggles.enabled" : "teleport.toggles.disabled", Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.tptoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.tptoggle.others"));
            return true;
        }

        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.TELEPORT);

        MessageUtil.send(player,
                newState ? "teleport.toggles.enabled-for" : "teleport.toggles.disabled-for",
                Map.of("player", target.getName()));

        MessageUtil.send(target,
                newState ? "teleport.toggles.enabled-by" : "teleport.toggles.disabled-by",
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
            if (!player.hasPermission("aircore.command.tptoggle.others")) {
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
