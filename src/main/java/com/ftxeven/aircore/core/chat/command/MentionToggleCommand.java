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

public final class MentionToggleCommand implements TabExecutor {

    private final AirCore plugin;

    public MentionToggleCommand(AirCore plugin) {
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

            boolean newState = plugin.core().toggles().toggle(target.getUniqueId(), ToggleService.Toggle.MENTIONS);
            String targetName = target.getName() != null ? target.getName() : args[0];
            sender.sendMessage("Mention toggle status for " + targetName + " -> "
                    + (newState ? "enabled" : "disabled"));

            if (target.isOnline() && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target.getPlayer(),
                        newState ? "chat.toggles.mentions.enabled-by" : "chat.toggles.mentions.disabled-by",
                        Map.of("player", consoleName));
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.mentiontoggle")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.mentiontoggle"));
            return true;
        }

        if (args.length == 0) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.MENTIONS);
            MessageUtil.send(player,
                    newState ? "chat.toggles.mentions.enabled" : "chat.toggles.mentions.disabled",
                    Map.of());
            return true;
        }

        OfflinePlayer resolved = resolve(player, args[0]);
        if (resolved == null) return true;

        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            boolean newState = plugin.core().toggles().toggle(player.getUniqueId(), ToggleService.Toggle.MENTIONS);
            MessageUtil.send(player,
                    newState ? "chat.toggles.mentions.enabled" : "chat.toggles.mentions.disabled",
                    Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.mentiontoggle.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.mentiontoggle.others"));
            return true;
        }

        boolean newState = plugin.core().toggles().toggle(resolved.getUniqueId(), ToggleService.Toggle.MENTIONS);
        String targetName = resolved.getName() != null ? resolved.getName() : args[0];

        MessageUtil.send(player,
                newState ? "chat.toggles.mentions.enabled-for" : "chat.toggles.mentions.disabled-for",
                Map.of("player", targetName));

        if (resolved.isOnline()) {
            MessageUtil.send(resolved.getPlayer(),
                    newState ? "chat.toggles.mentions.enabled-by" : "chat.toggles.mentions.disabled-by",
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

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.mentiontoggle.others")) {
                return List.of();
            }
        }

        return Bukkit.getOnlinePlayers().stream()
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
        } else {
            sender.sendMessage("Player not found");
        }
        return null;
    }
}