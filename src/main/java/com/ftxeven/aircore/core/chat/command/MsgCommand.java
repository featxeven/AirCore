package com.ftxeven.aircore.core.chat.command;

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

        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player|@a> <message>");
                return true;
            }

            String targetName = args[0];

            List<Player> recipients = new ArrayList<>();
            if (targetName.equalsIgnoreCase("@a")) {
                recipients.addAll(Bukkit.getOnlinePlayers());
                if (recipients.isEmpty()) {
                    sender.sendMessage("No players are currently online.");
                    return true;
                }
            } else {
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                recipients.add(target);
            }

            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <player|@a> <message>");
                return true;
            }

            String message = String.join(" ", args).substring(targetName.length()).trim();

            if (targetName.equalsIgnoreCase("@a")) {
                plugin.chat().messages().sendPrivateMessageEveryoneFromConsole(consoleName, recipients, message);
                sender.sendMessage("Message sent to everyone: " + message);
            } else {
                Player target = recipients.getFirst();
                plugin.chat().messages().sendPrivateMessageFromConsole(consoleName, target, message);
                sender.sendMessage("Message sent to " + target.getName() + ": " + message);
            }
            return true;
        }

        if (!player.hasPermission("aircore.command.msg")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.msg"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("msg", label)));
            return true;
        }

        String targetName = args[0];
        boolean bypassToggle = player.hasPermission("aircore.bypass.chat.toggle");

        if (targetName.equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.msg.all")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.msg.all"));
                return true;
            }
        } else {
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
                MessageUtil.send(player, "utilities.blocking.error-blocked-by",
                        Map.of("player", target.getName()));
                return true;
            }

            if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.PM)) {
                MessageUtil.send(player, "chat.private-messages.error-disabled",
                        Map.of("player", target.getName()));
                return true;
            }
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("msg", label)));
            return true;
        }

        String message = String.join(" ", args).substring(targetName.length()).trim();
        String sanitized = plugin.chat().formats().sanitizeForChat(player, message);
        String stripped = sanitized.replaceAll("<[^>]+>", "").trim();

        if (stripped.isEmpty()) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("msg", label)));
            return true;
        }

        if (targetName.equalsIgnoreCase("@a")) {
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
        } else {
            Player target = Bukkit.getPlayerExact(targetName);

            if (target != null) {
                plugin.chat().messages().sendPrivateMessage(player, target, message);

                if (plugin.utility().afk().isAfk(target.getUniqueId())) {
                    MessageUtil.send(player, "utilities.afk.interaction-notify",
                            Map.of("player", target.getName()));
                }
            } else {
                MessageUtil.send(player, "errors.player-not-found", Map.of());
            }
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
        boolean canSeeAll = !(sender instanceof Player) || sender.hasPermission("aircore.command.msg.all");

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