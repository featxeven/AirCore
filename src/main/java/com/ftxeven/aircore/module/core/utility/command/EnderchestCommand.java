package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.gui.GuiManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnderchestCommand implements TabExecutor {
    private final AirCore plugin;
    private final GuiManager guiManager;

    public EnderchestCommand(AirCore plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.enderchest")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.enderchest"));
            return true;
        }

        String targetName;

        if (args.length < 1) {
            targetName = player.getName();
        } else {
            if (!player.hasPermission("aircore.command.enderchest.others")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.enderchest.others"));
                return true;
            }

            OfflinePlayer target = resolve(player, args[0]);
            if (target == null) return true;

            if (target.getUniqueId().equals(player.getUniqueId())) {
                targetName = player.getName();
            } else {
                targetName = target.getName() != null ? target.getName() : args[0];
            }
        }

        final String finalTargetName = targetName;

        if (targetName.equalsIgnoreCase(player.getName())) {
            plugin.scheduler().runEntityTask(player, () ->
                    player.openInventory(player.getEnderChest())
            );
        } else {
            plugin.scheduler().runEntityTask(player, () ->
                    guiManager.openGui("enderchest", player,
                            Map.of("player", player.getName(), "target", finalTargetName))
            );
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.enderchest")) return List.of();

        if (args.length != 1) return List.of();

        if (!player.hasPermission("aircore.command.enderchest.others")) return List.of();

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }

    private OfflinePlayer resolve(Player player, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) {
            return Bukkit.getOfflinePlayer(cached);
        }

        MessageUtil.send(player, "errors.player-never-joined", Map.of("player", name));
        return null;
    }
}