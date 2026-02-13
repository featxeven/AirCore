package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.enderchest"));
            return true;
        }

        if (args.length == 0) {
            openOwnEnderchest(player);
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.enderchest.others");
        if (!hasOthers) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.enderchest.others"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("enderchest", "others", label)));
            return true;
        }

        String targetName = args[0];
        handleOthers(player, targetName);
        return true;
    }

    private void openOwnEnderchest(Player player) {
        plugin.scheduler().runEntityTask(player, () ->
                player.openInventory(guiManager.getEnderchestManager().buildOwn(player))
        );
    }

    private void handleOthers(Player player, String targetName) {
        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return;

        String finalTargetName = resolved.getName() != null ? resolved.getName() : targetName;

        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            openOwnEnderchest(player);
            return;
        }

        plugin.scheduler().runEntityTask(player, () ->
                guiManager.openGui("enderchest", player,
                        Map.of("player", player.getName(), "target", finalTargetName))
        );
    }

    private OfflinePlayer resolve(Player player, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        MessageUtil.send(player, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1 || !(sender instanceof Player player)) return Collections.emptyList();

        if (!player.hasPermission("aircore.command.enderchest") ||
                !player.hasPermission("aircore.command.enderchest.others")) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}