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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MsgCommand implements TabExecutor {

    private final AirCore plugin;

    public MsgCommand(AirCore plugin) {
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

            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <player|@a> <message>");
                return true;
            }

            String targetName = args[0];
            String message = String.join(" ", args).substring(args[0].length()).trim();

            if (targetName.equalsIgnoreCase("@a")) {
                List<Player> recipients = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (recipients.isEmpty()) {
                    sender.sendMessage(plugin.lang().get("errors.none-online"));
                    return true;
                }

                plugin.chat().messages().sendPrivateMessageEveryoneFromConsole(consoleName, recipients, message);
                sender.sendMessage("Message sent to everyone: " + message);
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(plugin.lang().get("errors.player-not-found"));
                return true;
            }

            plugin.chat().messages().sendPrivateMessageFromConsole(consoleName, target, message);
            sender.sendMessage("Message sent to " + target.getName() + ": " + message);
            return true;
        }

        // Player behavior
        if (!player.hasPermission("aircore.command.msg")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msg"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("msg", label)));
            return true;
        }

        boolean bypassToggle = player.hasPermission("aircore.bypass.chat.toggle");
        String targetName = args[0];

        // @a broadcast
        if (targetName.equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.msg.all")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.msg.all"));
                return true;
            }

            String message = String.join(" ", args).substring(args[0].length()).trim();
            String sanitized = plugin.chat().formats().sanitizeForChat(player, message);
            String stripped = sanitized.replaceAll("<[^>]+>", "").trim();
            if (stripped.isEmpty()) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("msg", label)));
                return true;
            }

            plugin.scheduler().runAsync(() -> {
                List<Player> recipients = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player))
                        .filter(p -> !plugin.core().blocks().isBlocked(p.getUniqueId(), player.getUniqueId()))
                        .filter(p -> bypassToggle || plugin.core().toggles().isEnabled(p.getUniqueId(), ToggleService.Toggle.PM))
                        .collect(Collectors.toList());

                int onlineOthers = Bukkit.getOnlinePlayers().size() - 1;

                plugin.scheduler().runTask(() -> {
                    if (recipients.isEmpty()) {
                        if (onlineOthers <= 0) {
                            MessageUtil.send(player, "errors.none-online", Map.of());
                        } else {
                            MessageUtil.send(player, "chat.private-messages.error-blocked", Map.of());
                        }
                        return;
                    }
                    plugin.chat().messages().sendPrivateMessageEveryone(player, recipients, message);
                });
            });
            return true;
        }

        // Direct message
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of());
            return true;
        }

        // Self-message
        if (target.equals(player)) {
            if (!plugin.config().pmAllowSelfMessage()) {
                MessageUtil.send(player, "chat.private-messages.error-self", Map.of());
                return true;
            }
        }

        // Block check
        if (plugin.core().blocks().isBlocked(target.getUniqueId(), player.getUniqueId())) {
            MessageUtil.send(player, "chat.blocking.error-blocked-by",
                    Map.of("player", target.getName()));
            return true;
        }

        // Toggle check
        if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.PM)) {
            MessageUtil.send(player, "chat.private-messages.error-disabled",
                    Map.of("player", target.getName()));
            return true;
        }

        String message = String.join(" ", args).substring(args[0].length()).trim();
        String sanitized = plugin.chat().formats().sanitizeForChat(player, message);
        String stripped = sanitized.replaceAll("<[^>]+>", "").trim();

        if (stripped.isEmpty()) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("msg", label)));
            return true;
        }

        plugin.chat().messages().sendPrivateMessage(player, target, message);

        // AFK notify check
        if (plugin.utility().afk().isAfk(target.getUniqueId())) {
            MessageUtil.send(player, "utilities.afk.interaction-notify",
                    Map.of("player", target.getName()));
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
            if (!player.hasPermission("aircore.command.msg")) {
                return List.of();
            }
        }

        List<String> completions = new ArrayList<>();

        boolean canSeeAll = !(sender instanceof Player)
                || sender.hasPermission("aircore.command.msg.all");

        if (canSeeAll && "@a".startsWith(input)) {
            completions.add("@a");
        }

        completions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList());

        return completions;
    }
}