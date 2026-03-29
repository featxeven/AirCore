package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class RepairCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.repair";
    private static final String PERMISSION_ALL = "aircore.command.repair.all";

    public RepairCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        String allSel = plugin.commandConfig().getSelector("repair", "all");
        boolean hasAllPerm = player.hasPermission(PERMISSION_ALL);

        if (args.length > (hasAllPerm ? 1 : 0)) {
            sendError(player, label, hasAllPerm ? "all" : null, "too-many-arguments");
            return true;
        }

        if (args.length == 1) {
            if (!args[0].equalsIgnoreCase(allSel)) {
                sendError(player, label, "all", "incorrect-usage");
                return true;
            }
            handleRepairAll(player);
            return true;
        }

        handleRepairHand(player);
        return true;
    }

    private void handleRepairHand(Player player) {
        plugin.scheduler().runEntityTask(player, () -> {
            ItemStack item = player.getInventory().getItemInMainHand();

            if (isNotRepairable(item)) {
                MessageUtil.send(player, "utilities.repair.cannot-repair", Map.of());
                return;
            }

            Damageable meta = (Damageable) item.getItemMeta();
            if (meta == null) return;

            if (meta.hasDamage()) {
                meta.setDamage(0);
                item.setItemMeta(meta);
                MessageUtil.send(player, "utilities.repair.success", Map.of());
            } else {
                MessageUtil.send(player, "utilities.repair.error-not-damaged", Map.of());
            }
        });
    }

    private void handleRepairAll(Player player) {
        plugin.scheduler().runEntityTask(player, () -> {
            boolean repairedAny = false;
            ItemStack[] contents = player.getInventory().getContents();

            for (ItemStack item : contents) {
                if (isNotRepairable(item)) continue;

                Damageable meta = (Damageable) item.getItemMeta();
                if (meta != null && meta.hasDamage()) {
                    meta.setDamage(0);
                    item.setItemMeta(meta);
                    repairedAny = true;
                }
            }

            if (repairedAny) {
                MessageUtil.send(player, "utilities.repair.success-all", Map.of());
            } else {
                MessageUtil.send(player, "utilities.repair.error-none-damaged", Map.of());
            }
        });
    }

    private boolean isNotRepairable(ItemStack item) {
        return item == null || item.getType() == Material.AIR ||
                item.getType().getMaxDurability() <= 0 ||
                !(item.getItemMeta() instanceof Damageable);
    }

    private void sendError(Player player, String label, String variant, String key) {
        String usage = plugin.commandConfig().getUsage("repair", variant, label);
        MessageUtil.send(player, "errors." + key, Map.of("usage", usage));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION_ALL)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String allSel = plugin.commandConfig().getSelector("repair", "all");
            String input = args[0].toLowerCase();
            if (allSel.toLowerCase().startsWith(input)) {
                return List.of(allSel);
            }
        }

        return Collections.emptyList();
    }
}