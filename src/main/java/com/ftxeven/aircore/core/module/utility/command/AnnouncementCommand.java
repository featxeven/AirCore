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
    private static final String PERMISSION = "aircore.command.announcement";

    public AnnouncementCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String selTrigger = plugin.commandConfig().getSelector("announcement", "trigger");
        String selEnable = plugin.commandConfig().getSelector("announcement", "enable");
        String selDisable = plugin.commandConfig().getSelector("announcement", "disable");

        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <" + selTrigger + "|" + selEnable + "|" + selDisable + "> <key> [args]");
                return true;
            }
            handleExecute(sender, label, args, selTrigger, selEnable, selDisable);
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (args.length == 0) {
            sendError(player, label, null);
            return true;
        }

        handleExecute(player, label, args, selTrigger, selEnable, selDisable);
        return true;
    }

    private void handleExecute(CommandSender sender, String label, String[] args, String selTrigger, String selEnable, String selDisable) {
        String sub = args[0].toLowerCase();

        String variant;
        if (sub.equalsIgnoreCase(selTrigger)) variant = "trigger";
        else if (sub.equalsIgnoreCase(selEnable)) variant = "enable";
        else if (sub.equalsIgnoreCase(selDisable)) variant = "disable";
        else variant = null;

        if (variant == null) {
            if (sender instanceof Player p) sendError(p, label, null);
            else sender.sendMessage("Unknown sub-command");
            return;
        }

        if (args.length < 2) {
            if (sender instanceof Player p) {
                sendError(p, label, variant);
            } else {
                sender.sendMessage("Usage: /" + label + " " + sub + " <key>");
            }
            return;
        }

        String key = args[1].toLowerCase();

        if (variant.equals("trigger")) {
            String triggerArgs = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;

            if (plugin.announcements().getAnnouncement(key) == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "utilities.announcement.not-found", Map.of("name", key));
                else sender.sendMessage("Announcement not found: " + key);
                return;
            }

            plugin.announcements().trigger(key, triggerArgs);
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.announcement.triggered", Map.of("name", key));
            else sender.sendMessage("Triggered announcement: " + key);

        } else {
            boolean enable = variant.equals("enable");
            AnnouncementManager.ToggleResult result = plugin.announcements().setEnabled(key, enable);

            if (sender instanceof Player p) {
                handleToggleResultPlayer(p, key, enable, result);
            } else {
                handleToggleResultConsole(sender, key, enable, result);
            }
        }
    }

    private void handleToggleResultPlayer(Player player, String key, boolean enable, AnnouncementManager.ToggleResult result) {
        switch (result) {
            case SUCCESS -> MessageUtil.send(player, "utilities.announcement." + (enable ? "enabled" : "disabled"), Map.of("name", key));
            case ALREADY_SET -> MessageUtil.send(player, "utilities.announcement." + (enable ? "already-enabled" : "already-disabled"), Map.of("name", key));
            case NOT_FOUND -> MessageUtil.send(player, "utilities.announcement.not-found", Map.of("name", key));
        }
    }

    private void handleToggleResultConsole(CommandSender sender, String key, boolean enable, AnnouncementManager.ToggleResult result) {
        switch (result) {
            case SUCCESS -> sender.sendMessage((enable ? "Enabled" : "Disabled") + " announcement: " + key);
            case ALREADY_SET -> sender.sendMessage("Announcement " + key + " is already " + (enable ? "enabled" : "disabled") + ".");
            case NOT_FOUND -> sender.sendMessage("Announcement not found");
        }
    }

    private void sendError(Player player, String label, String sub) {
        String usage = plugin.commandConfig().getUsage("announcement", sub, label);
        MessageUtil.send(player, "errors." + "incorrect-usage", Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERMISSION)) return Collections.emptyList();

        String selTrigger = plugin.commandConfig().getSelector("announcement", "trigger");
        String selEnable = plugin.commandConfig().getSelector("announcement", "enable");
        String selDisable = plugin.commandConfig().getSelector("announcement", "disable");

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return Stream.of(selTrigger, selEnable, selDisable)
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals(selTrigger) || sub.equals(selEnable) || sub.equals(selDisable)) {
                return plugin.announcements().getRegistry().keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}