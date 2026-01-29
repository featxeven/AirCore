package com.ftxeven.aircore.module.core.chat.command;

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

public final class MsgToggleCommand implements TabExecutor {

    private final AirCore plugin;

    public MsgToggleCommand(AirCore plugin) {
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

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PM);

            sender.sendMessage("Msgtoggle status for " + target.getName() + " -> "
                    + (newState ? "enabled" : "disabled"));

            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target,
                        newState ? "chat.toggles.messages.enabled-by" : "chat.toggles.messages.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        // Player
        if (!player.hasPermission("aircore.command.msgtoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msgtoggle"));
            return true;
        }

        // Self toggle
        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PM);
            MessageUtil.send(player,
                    newState ? "chat.toggles.messages.enabled" : "chat.toggles.messages.disabled",
                    Map.of());
            return true;
        }

        // Toggle another player
        if (!player.hasPermission("aircore.command.msgtoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msgtoggle.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PM);

        MessageUtil.send(player,
                newState ? "chat.toggles.messages.enabled-for" : "chat.toggles.messages.disabled-for",
                Map.of("player", target.getName()));

        MessageUtil.send(target,
                newState ? "chat.toggles.messages.enabled-by" : "chat.toggles.messages.disabled-by",
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
            if (!player.hasPermission("aircore.command.msgtoggle.others")) {
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
