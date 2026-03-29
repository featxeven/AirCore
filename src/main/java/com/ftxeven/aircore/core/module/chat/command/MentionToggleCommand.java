package com.ftxeven.aircore.core.module.chat.command;

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

public final class MentionToggleCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.mentiontoggle";
    private static final String PERM_OTHERS = "aircore.command.mentiontoggle.others";

    public MentionToggleCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleToggle(sender, args[0]);
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);

        if (args.length == 0) {
            handleToggle(player, player.getName());
            return true;
        }

        if (!hasOthers || args.length > 1) {
            sendError(player, label, hasOthers);
            return true;
        }

        handleToggle(player, args[0]);
        return true;
    }

    private void handleToggle(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        UUID uuid = resolved.getUniqueId();
        boolean newState = plugin.core().toggles().toggle(uuid, ToggleService.Toggle.MENTIONS);
        String realName = plugin.database().records().getRealName(targetName);
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (sender instanceof Player p) {
            if (uuid.equals(p.getUniqueId())) {
                MessageUtil.send(p, newState ? "chat.toggles.mentions.enabled" : "chat.toggles.mentions.disabled", Map.of());
            } else {
                MessageUtil.send(p, newState ? "chat.toggles.mentions.enabled-for" : "chat.toggles.mentions.disabled-for",
                        Map.of("player", realName));

                if (resolved.isOnline() && resolved.getPlayer() != null) {
                    MessageUtil.send(resolved.getPlayer(), newState ? "chat.toggles.mentions.enabled-by" : "chat.toggles.mentions.disabled-by",
                            Map.of("player", senderName));
                }
            }
        } else {
            sender.sendMessage("Mention toggle for " + realName + " -> " + (newState ? "enabled" : "disabled"));
            if (resolved.isOnline() && resolved.getPlayer() != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(resolved.getPlayer(), newState ? "chat.toggles.mentions.enabled-by" : "chat.toggles.mentions.disabled-by",
                        Map.of("player", senderName));
            }
        }
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("mentiontoggle", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        else sender.sendMessage("Player not found");
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return Collections.emptyList();
        if (sender instanceof Player && !sender.hasPermission(PERM_OTHERS)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}