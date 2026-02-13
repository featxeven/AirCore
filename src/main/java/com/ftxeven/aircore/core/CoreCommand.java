package com.ftxeven.aircore.core;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CoreCommand implements TabExecutor {

    private final AirCore plugin;

    public CoreCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (sender instanceof Player player && !player.hasPermission("aircore.command.admin")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.admin"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender, args);
            case "version" -> {
                String version = plugin.getPluginMeta().getVersion();
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "general.plugin-version", Map.of("version", version));
                } else {
                    sender.sendMessage("Plugin version is " + version);
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "all";

        switch (type) {
            case "placeholders" -> {
                plugin.placeholders().reload();
                notifyReload(sender, "placeholders");
            }
            case "config" -> {
                plugin.config().reload();
                plugin.core().reload();
                notifyReload(sender, "config");
            }
            case "guis" -> {
                plugin.gui().reload();
                notifyReload(sender, "guis");
            }
            case "messages" -> {
                plugin.lang().reload();
                notifyReload(sender, "messages");
            }
            case "all" -> {
                plugin.config().reload();
                plugin.lang().reload();
                plugin.core().reload();
                plugin.gui().reload();
                plugin.chat().reload();
                plugin.economy().reload();
                plugin.home().reload();
                plugin.kit().reload();
                plugin.teleport().reload();
                plugin.utility().reload();
                plugin.placeholders().reload();
                notifyReload(sender, "all");
            }
            default -> {
                if (sender instanceof Player) {
                    sendUsage(sender);
                } else {
                    sender.sendMessage("Unknown reload type. Use: placeholders, config, guis, messages, or all.");
                }
            }
        }
    }

    private void notifyReload(CommandSender sender, String moduleKey) {
        if (sender instanceof Player p) {
            String translatedModule = plugin.lang().get("general.modules." + moduleKey);
            MessageUtil.send(p, "general.plugin-reloaded", Map.of("module", translatedModule));
        } else {
            String hardcoded = moduleKey.substring(0, 1).toUpperCase() + moduleKey.substring(1);
            sender.sendMessage(hardcoded + " reloaded successfully.");
        }
    }

    private void sendUsage(CommandSender sender) {
        if (sender instanceof Player p) {
            MessageUtil.send(p, "general.plugin-usage", Map.of());
        } else {
            sender.sendMessage("Usage: /aircore <reload [type]|version>");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (sender instanceof Player player && !player.hasPermission("aircore.command.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Stream.of("reload", "version")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return Stream.of("all", "placeholders", "config", "guis", "messages")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}