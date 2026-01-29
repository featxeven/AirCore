package com.ftxeven.aircore.module.core;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.admin")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.admin"));
                return true;
            }
        }

        if (args.length == 0) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "general.plugin-usage", Map.of());
            } else {
                sender.sendMessage("Usage: /aircore <reload|version>");
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
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

                if (sender instanceof Player p) {
                    MessageUtil.send(p, "general.plugin-reloaded", Map.of());
                } else {
                    sender.sendMessage("Plugin reloaded successfully");
                }
            }
            case "version" -> {
                String version = plugin.getDescription().getVersion();
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "general.plugin-version", Map.of("version", version));
                } else {
                    sender.sendMessage("Plugin version is " + version);
                }
            }
            default -> {
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "general.plugin-usage", Map.of());
                } else {
                    sender.sendMessage("Usage: /aircore <reload|version>");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            return Stream.of("reload", "version")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
