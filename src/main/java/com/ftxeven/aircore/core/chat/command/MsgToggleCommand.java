package com.ftxeven.aircore.core.chat.command;

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
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleToggle(sender, args[0], plugin.lang().get("general.console-name"), true);
            return true;
        }

        if (!player.hasPermission("aircore.command.msgtoggle")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.msgtoggle"));
            return true;
        }

        if (args.length == 0) {
            handleToggle(player, player.getName(), player.getName(), false);
            return true;
        }

        if (!player.hasPermission("aircore.command.msgtoggle.others")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.msgtoggle.others"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("msgtoggle", "others", label)));
            return true;
        }

        handleToggle(player, args[0], player.getName(), false);
        return true;
    }

    private void handleToggle(CommandSender sender, String targetName, String senderName, boolean isConsole) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        UUID uuid = resolved.getUniqueId();
        boolean newState = plugin.core().toggles().toggle(uuid, ToggleService.Toggle.PM);
        String finalTargetName = resolved.getName() != null ? resolved.getName() : targetName;

        if (sender instanceof Player p) {
            if (uuid.equals(p.getUniqueId())) {
                MessageUtil.send(p, newState ? "chat.toggles.messages.enabled" : "chat.toggles.messages.disabled", Map.of());
            } else {
                MessageUtil.send(p, newState ? "chat.toggles.messages.enabled-for" : "chat.toggles.messages.disabled-for",
                        Map.of("player", finalTargetName));
            }
        } else {
            sender.sendMessage("Msgtoggle status for " + finalTargetName + " -> " + (newState ? "enabled" : "disabled"));
        }

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player onlineTarget = resolved.getPlayer();
            if (sender instanceof Player p && onlineTarget.equals(p)) return;

            if (!isConsole || plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(onlineTarget, newState ? "chat.toggles.messages.enabled-by" : "chat.toggles.messages.disabled-by",
                        Map.of("player", senderName));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        if (sender instanceof Player player && !player.hasPermission("aircore.command.msgtoggle.others")) {
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