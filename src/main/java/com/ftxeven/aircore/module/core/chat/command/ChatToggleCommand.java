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

public final class ChatToggleCommand implements TabExecutor {

    private final AirCore plugin;

    public ChatToggleCommand(AirCore plugin) {
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
                sender.sendMessage(plugin.lang().get("errors.player-not-found"));
                return true;
            }

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.CHAT);

            sender.sendMessage("Chat toggle status for " + target.getName() + " -> "
                    + (newState ? "enabled" : "disabled"));

            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target,
                        newState ? "chat.toggles.chat.enabled-by" : "chat.toggles.chat.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        // Player behavior
        if (!player.hasPermission("aircore.command.chattoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.chattoggle"));
            return true;
        }

        // Self toggle
        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.CHAT);
            MessageUtil.send(player,
                    newState ? "chat.toggles.chat.enabled" : "chat.toggles.chat.disabled",
                    Map.of());
            return true;
        }

        // /chattoggle <player>
        Player target = Bukkit.getPlayerExact(args[0]);

        // Self-target
        if (target != null && target.equals(player)) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.CHAT);
            MessageUtil.send(player,
                    newState ? "chat.toggles.chat.enabled" : "chat.toggles.chat.disabled",
                    Map.of());
            return true;
        }

        // Requires .others
        if (!player.hasPermission("aircore.command.chattoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.chattoggle.others"));
            return true;
        }

        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.CHAT);

        MessageUtil.send(player,
                newState ? "chat.toggles.chat.enabled-for" : "chat.toggles.chat.disabled-for",
                Map.of("player", target.getName()));

        MessageUtil.send(target,
                newState ? "chat.toggles.chat.enabled-by" : "chat.toggles.chat.disabled-by",
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
            if (!player.hasPermission("aircore.command.chattoggle.others")) {
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
