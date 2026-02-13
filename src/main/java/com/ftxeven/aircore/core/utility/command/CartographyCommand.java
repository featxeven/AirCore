package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CartographyCommand implements TabExecutor {

    private final AirCore plugin;

    public CartographyCommand(AirCore plugin) {
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

        if (!player.hasPermission("aircore.command.cartography")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.cartography"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage("cartography", label)));
            return true;
        }

        plugin.scheduler().runEntityTask(player, () -> {
            Inventory cartography = Bukkit.createInventory(player, InventoryType.CARTOGRAPHY);
            player.openInventory(cartography);
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}