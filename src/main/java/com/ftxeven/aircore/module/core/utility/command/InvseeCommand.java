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

public final class InvseeCommand implements TabExecutor {
    private final AirCore plugin;
    private final GuiManager guiManager;

    public InvseeCommand(AirCore plugin, GuiManager guiManager) {
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

        if (!player.hasPermission("aircore.command.invsee")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.invsee"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("invsee", label)));
            return true;
        }

        OfflinePlayer target = resolve(player, args[0]);
        if (target == null) return true;

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "utilities.errors.invsee-self", Map.of());
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];

        plugin.scheduler().runEntityTask(player, () ->
                guiManager.openGui("inventory", player,
                        Map.of("player", player.getName(), "target", targetName))
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.invsee")) return List.of();

        if (args.length != 1) return List.of();

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