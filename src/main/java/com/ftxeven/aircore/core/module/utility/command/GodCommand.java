package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
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

public final class GodCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.god";
    private static final String PERM_OTHERS = "aircore.command.god.others";

    public GodCommand(AirCore plugin) {
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
            handleGod(sender, args[0]);
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);

        if (args.length == 0) {
            handleGod(player, player.getName());
            return true;
        }

        if (!hasOthers) {
            sendError(player, label, false);
            return true;
        }

        if (args.length > 1) {
            sendError(player, label, true);
            return true;
        }

        handleGod(player, args[0]);
        return true;
    }

    private void handleGod(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        UUID uuid = resolved.getUniqueId();
        String realName = resolved.getName() != null ? resolved.getName() : targetName;
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        boolean newState = !plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.GOD);

        plugin.scheduler().runAsync(() -> {
            plugin.core().toggles().setLocal(uuid, ToggleService.Toggle.GOD, newState);
            plugin.database().records().setToggle(uuid, ToggleService.Toggle.GOD.getColumn(), newState);

            plugin.scheduler().runTask(() -> {
                if (sender instanceof Player p) {
                    if (uuid.equals(p.getUniqueId())) {
                        MessageUtil.send(p, newState ? "utilities.godmode.enabled" : "utilities.godmode.disabled", Map.of());
                    } else {
                        MessageUtil.send(p, newState ? "utilities.godmode.enabled-for" : "utilities.godmode.disabled-for", Map.of("player", realName));
                    }
                } else {
                    sender.sendMessage("God mode for " + realName + " -> " + (newState ? "enabled" : "disabled"));
                }

                if (resolved.isOnline() && resolved.getPlayer() != null) {
                    Player onlineTarget = resolved.getPlayer();
                    if (sender instanceof Player p && onlineTarget.equals(p)) return;
                    if (!(sender instanceof Player) && !plugin.config().consoleToPlayerFeedback()) return;

                    MessageUtil.send(onlineTarget, newState ? "utilities.godmode.enabled-by" : "utilities.godmode.disabled-by", Map.of("player", senderName));
                }
            });
        });
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("god", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors." + "too-many-arguments", Map.of("usage", usage));
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
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
}