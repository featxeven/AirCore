package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.config.AnnouncementManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AnnouncementCommand implements TabExecutor {

    private final AirCore plugin;

    public AnnouncementCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /" + label + " <trigger|enable|disable> <key> [args]");
                return true;
            }
            handleConsoleCommand(sender, label, args);
            return true;
        }

        if (!player.hasPermission("aircore.command.announcement")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.announcement"));
            return true;
        }

        if (args.length == 0) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("announcement", label)));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "trigger" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "errors.incorrect-usage",
                            Map.of("usage", plugin.config().getUsage("announcement", "trigger", label)));
                    return true;
                }

                String key = args[1].toLowerCase();
                if (plugin.announcements().getAnnouncement(key) == null) {
                    MessageUtil.send(player, "utilities.announcement.not-found", Map.of("name", key));
                    return true;
                }

                String triggerArgs = args.length > 2
                        ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : null;

                plugin.announcements().trigger(key, triggerArgs);
                MessageUtil.send(player, "utilities.announcement.triggered", Map.of("name", key));
            }

            case "enable", "disable" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "errors.incorrect-usage",
                            Map.of("usage", plugin.config().getUsage("announcement", sub, label)));
                    return true;
                }

                boolean enable = sub.equals("enable");
                String key = args[1].toLowerCase();

                AnnouncementManager.ToggleResult result = plugin.announcements().setEnabled(key, enable);

                switch (result) {
                    case SUCCESS -> {
                        String langKey = "utilities.announcement." + (enable ? "enabled" : "disabled");
                        MessageUtil.send(player, langKey, Map.of("name", key));
                    }
                    case ALREADY_SET -> {
                        String langKey = "utilities.announcement." + (enable ? "already-enabled" : "already-disabled");
                        MessageUtil.send(player, langKey, Map.of("name", key));
                    }
                    case NOT_FOUND -> {
                        MessageUtil.send(player, "utilities.announcement.not-found", Map.of("name", key));
                    }
                }
            }

            default -> MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("announcement", label)));
        }

        return true;
    }

    private void handleConsoleCommand(CommandSender sender, String label, String[] args) {
        String sub = args[0].toLowerCase();

        if (sub.equals("trigger")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " trigger <key> [args]");
                return;
            }
            String triggerArgs = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            plugin.announcements().trigger(args[1], triggerArgs);
            sender.sendMessage("Triggered announcement: " + args[1]);
        } else if (sub.equals("enable") || sub.equals("disable")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " " + sub + " <key>");
                return;
            }
            boolean enable = sub.equals("enable");
            String key = args[1];
            AnnouncementManager.ToggleResult result = plugin.announcements().setEnabled(key, enable);

            switch (result) {
                case SUCCESS -> sender.sendMessage((enable ? "Enabled" : "Disabled") + " announcement: " + key);
                case ALREADY_SET -> sender.sendMessage("Announcement " + key + " is already " + (enable ? "enabled" : "disabled") + ".");
                case NOT_FOUND -> sender.sendMessage("Announcement not found: " + key);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (!(sender instanceof Player player) || !player.hasPermission("aircore.command.announcement")) {
            return Collections.emptyList();
        }

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return Stream.of("trigger", "enable", "disable")
                    .filter(s -> s.startsWith(input))
                    .toList();
        }

        if (args.length == 2) {
            return plugin.announcements().getRegistry().keySet().stream()
                    .filter(name -> name.startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}