package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class CraftingTableCommand implements TabExecutor {

    private final AirCore plugin;

    public CraftingTableCommand(AirCore plugin) {
        this.plugin = plugin;
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

        if (!player.hasPermission("aircore.command.craftingtable")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.craftingtable"));
            return true;
        }

        plugin.scheduler().runEntityTask(player, () -> {
            Inventory workbench = Bukkit.createInventory(player, InventoryType.WORKBENCH);
            player.openInventory(workbench);
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return List.of();
    }
}