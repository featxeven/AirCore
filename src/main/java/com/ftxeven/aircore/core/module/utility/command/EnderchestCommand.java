package com.ftxeven.aircore.core.module.utility.command;

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
    private static final String PERM_BASE = "aircore.command.enderchest";
    private static final String PERM_OTHERS = "aircore.command.enderchest.others";

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

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);

        if (args.length == 0) {
            handleEnderchest(player, player.getName());
            return true;
        }

        if (!hasOthers) {
            sendError(player, label, false);
            return true;
        }

        if (args.length > 1) {
            sendError(player, label, true);
            return true;
        }

        handleEnderchest(player, args[0]);
        return true;
    }

    private void handleEnderchest(Player player, String targetName) {
        OfflinePlayer resolved = resolve(player, targetName);
        if (resolved == null) return;

        String realName = plugin.database().records().getRealName(targetName);

        if (resolved.getUniqueId().equals(player.getUniqueId())) {
            plugin.scheduler().runEntityTask(player, () ->
                    player.openInventory(guiManager.getEnderchestManager().buildOwn(player))
            );
            return;
        }

        plugin.scheduler().runEntityTask(player, () ->
                guiManager.openGui("enderchest", player,
                        Map.of("player", player.getName(), "target", realName))
        );
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("enderchest", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    private OfflinePlayer resolve(Player player, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        MessageUtil.send(player, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();

        if (!player.hasPermission(PERM_OTHERS)) {
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