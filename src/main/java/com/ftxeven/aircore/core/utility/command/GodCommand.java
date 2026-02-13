package com.ftxeven.aircore.core.utility.command;

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
import java.util.Map;
import java.util.UUID;

public final class GodCommand implements TabExecutor {

    private final AirCore plugin;

    public GodCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // --- CONSOLE / NON-PLAYER BEHAVIOR ---
        if (!(sender instanceof Player player)) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            OfflinePlayer resolved = resolve(sender, args[0]);
            if (resolved == null) return true;

            handleToggle(sender, resolved, plugin.lang().get("general.console-name"), true);
            return true;
        }

        // --- PLAYER BEHAVIOR ---
        if (!player.hasPermission("aircore.command.god")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.god"));
            return true;
        }

        // Self toggle: /god
        if (args.length == 0) {
            handleToggle(player, player, player.getName(), false);
            return true;
        }

        // Target toggle: /god <player>
        if (!player.hasPermission("aircore.command.god.others")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.god.others"));
            return true;
        }

        OfflinePlayer resolved = resolve(player, args[0]);
        if (resolved == null) return true;

        handleToggle(player, resolved, player.getName(), false);
        return true;
    }

    private void handleToggle(CommandSender sender, OfflinePlayer target, String senderName, boolean isConsole) {
        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        boolean currentState = plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.GOD);
        boolean newState = !currentState;

        plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.GOD, newState);
        plugin.database().records().setToggle(uuid, ToggleService.Toggle.GOD.getColumn(), newState);

        if (sender instanceof Player p) {
            if (target.getUniqueId().equals(p.getUniqueId())) {
                MessageUtil.send(p, newState ? "utilities.godmode.enabled" : "utilities.godmode.disabled", Map.of());
            } else {
                MessageUtil.send(p, newState ? "utilities.godmode.enabled-for" : "utilities.godmode.disabled-for",
                        Map.of("player", targetName));
            }
        } else {
            sender.sendMessage("God mode for " + targetName + " -> " + (newState ? "enabled" : "disabled"));
        }

        if (target.isOnline() && target.getPlayer() != null) {
            Player onlineTarget = target.getPlayer();
            if (sender instanceof Player p && onlineTarget.equals(p)) return;

            if (isConsole) {
                if (plugin.config().consoleToPlayerFeedback()) {
                    MessageUtil.send(onlineTarget, newState ? "utilities.godmode.enabled-by" : "utilities.godmode.disabled-by",
                            Map.of("player", senderName));
                }
            } else {
                MessageUtil.send(onlineTarget, newState ? "utilities.godmode.enabled-by" : "utilities.godmode.disabled-by",
                        Map.of("player", senderName));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        if (sender instanceof Player player && !player.hasPermission("aircore.command.god.others")) {
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

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found.");
        }
        return null;
    }
}