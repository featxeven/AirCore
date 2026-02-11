package com.ftxeven.aircore.module.core.chat.command;

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

        if (!(sender instanceof Player player)) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            OfflinePlayer target = resolve(null, args[0]);
            if (target == null) return true;

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PM);
            String targetName = target.getName() != null ? target.getName() : args[0];

            sender.sendMessage("Msgtoggle status for " + targetName + " -> " + (newState ? "enabled" : "disabled"));

            if (target.isOnline() && plugin.config().consoleToPlayerFeedback()) {
                String consoleName = plugin.lang().get("general.console-name");
                MessageUtil.send(target.getPlayer(),
                        newState ? "chat.toggles.messages.enabled-by" : "chat.toggles.messages.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.msgtoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msgtoggle"));
            return true;
        }

        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PM);
            MessageUtil.send(player,
                    newState ? "chat.toggles.messages.enabled" : "chat.toggles.messages.disabled",
                    Map.of());
            return true;
        }

        OfflinePlayer target = resolve(player, args[0]);
        if (target == null) return true;

        if (target.getUniqueId().equals(player.getUniqueId())) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.PM);
            MessageUtil.send(player,
                    newState ? "chat.toggles.messages.enabled" : "chat.toggles.messages.disabled",
                    Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.msgtoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msgtoggle.others"));
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.PM);
        String targetName = target.getName() != null ? target.getName() : args[0];

        MessageUtil.send(player,
                newState ? "chat.toggles.messages.enabled-for" : "chat.toggles.messages.disabled-for",
                Map.of("player", targetName));

        if (target.isOnline()) {
            MessageUtil.send(target.getPlayer(),
                    newState ? "chat.toggles.messages.enabled-by" : "chat.toggles.messages.disabled-by",
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
                    if (sender.hasPermission("aircore.command.msgtoggle.others")) return true;
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
            MessageUtil.send(p, "errors.player-never-joined", Map.of("player", name));
        } else if (sender != null) {
            sender.sendMessage("Player not found");
        }
        return null;
    }
}