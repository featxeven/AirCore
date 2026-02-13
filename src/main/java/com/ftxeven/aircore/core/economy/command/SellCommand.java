package com.ftxeven.aircore.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.sell.SellManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class SellCommand implements TabExecutor {

    private final GuiManager guiManager;
    private final AirCore plugin;

    public SellCommand(AirCore plugin, GuiManager guiManager) {
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

        if (!player.hasPermission("aircore.command.sell")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.sell"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("sell", label)));
            return true;
        }

        SellManager sellManager = (SellManager) guiManager.getManager("sell");

        if (sellManager != null && sellManager.isEnabled()) {
            guiManager.openGui("sell", player, Map.of("player", player.getName()));
        } else {
            ItemStack inHand = player.getInventory().getItemInMainHand();

            if (inHand.getType().isAir()) {
                MessageUtil.send(player, "economy.sell.error-invalid", Map.of());
                return true;
            }

            double worthPerItem = plugin.economy().worth().getWorth(inHand);
            double worth = worthPerItem * inHand.getAmount();

            if (worth <= 0) {
                MessageUtil.send(player, "economy.sell.error-failed", Map.of());
                return true;
            }

            double rounded = plugin.economy().formats().round(worth);
            String formatted = plugin.economy().formats().formatAmount(rounded);

            var result = plugin.economy().transactions().deposit(player.getUniqueId(), rounded);

            if (result.type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(player, "economy.sell.success", Map.of("amount", formatted));
                player.getInventory().setItemInMainHand(null);
            } else {
                MessageUtil.send(player, "economy.sell.error-failed", Map.of());
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        return List.of();
    }
}