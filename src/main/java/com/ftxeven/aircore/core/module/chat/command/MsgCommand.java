package com.ftxeven.aircore.core.module.chat.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class MsgCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.msg";
    private static final String PERM_ALL = "aircore.command.msg.all";

    public MsgCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String allSelector = plugin.commandConfig().getSelector("msg", "all");

        if (!(sender instanceof Player player)) {
            handleConsole(sender, label, args, allSelector);
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("msg", label)));
            return true;
        }

        String targetName = args[0];
        String rawMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean bypassToggle = player.hasPermission("aircore.bypass.chat.toggle");

        if (targetName.equalsIgnoreCase(allSelector)) {
            if (!player.hasPermission(PERM_ALL)) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
            handleBroadcast(player, rawMessage, bypassToggle);
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        if (target.equals(player) && !plugin.config().pmAllowSelfMessage()) {
            MessageUtil.send(player, "chat.private-messages.error-self", Map.of());
            return true;
        }

        if (plugin.core().blocks().isBlocked(target.getUniqueId(), player.getUniqueId())) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by", Map.of("player", target.getName()));
            return true;
        }

        if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.PM)) {
            MessageUtil.send(player, "chat.private-messages.error-disabled", Map.of("player", target.getName()));
            return true;
        }

        String sanitized = plugin.chat().formats().sanitizeForChat(player, rawMessage);
        if (sanitized.replaceAll("<[^>]+>", "").trim().isEmpty()) return true;

        plugin.chat().messages().sendPrivateMessage(player, target, rawMessage);

        if (plugin.utility().afk().isAfk(target.getUniqueId())) {
            MessageUtil.send(player, "utilities.afk.interaction-notify", Map.of("player", target.getName()));
        }

        return true;
    }

    private void handleBroadcast(Player player, String message, boolean bypassToggle) {
        plugin.scheduler().runAsync(() -> {
            List<Player> recipients = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .filter(p -> !plugin.core().blocks().isBlocked(p.getUniqueId(), player.getUniqueId()))
                    .filter(p -> bypassToggle || plugin.core().toggles().isEnabled(p.getUniqueId(), ToggleService.Toggle.PM))
                    .map(Player.class::cast)
                    .toList();

            plugin.scheduler().runTask(() -> {
                if (recipients.isEmpty()) {
                    MessageUtil.send(player, Bukkit.getOnlinePlayers().size() <= 1 ? "errors.no-players-online" : "chat.private-messages.error-blocked", Map.of());
                    return;
                }
                plugin.chat().messages().sendPrivateMessageEveryone(player, recipients, message);
            });
        });
    }

    private void handleConsole(CommandSender sender, String label, String[] args, String allSelector) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " <player|" + allSelector + "> <message>");
            return;
        }

        String targetName = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String consoleName = String.valueOf(plugin.lang().get("general.console-name"));

        if (targetName.equalsIgnoreCase(allSelector)) {
            List<Player> recipients = Bukkit.getOnlinePlayers().stream()
                    .map(Player.class::cast)
                    .toList();

            if (recipients.isEmpty()) {
                sender.sendMessage("No players online.");
                return;
            }
            plugin.chat().messages().sendPrivateMessageEveryoneFromConsole(consoleName, recipients, message);
            sender.sendMessage("Broadcast message sent.");
        } else {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return;
            }
            plugin.chat().messages().sendPrivateMessageFromConsole(consoleName, target, message);
            sender.sendMessage("Message sent to " + target.getName());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return List.of();

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();
        String allSelector = plugin.commandConfig().getSelector("msg", "all");

        if (sender.hasPermission(PERM_ALL) && allSelector.startsWith(input)) {
            completions.add(allSelector);
        }

        completions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList());

        return completions;
    }
}