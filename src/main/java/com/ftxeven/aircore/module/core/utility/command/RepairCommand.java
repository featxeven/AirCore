package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RepairCommand implements TabExecutor {

    private final AirCore plugin;

    public RepairCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (!player.hasPermission("aircore.command.repair")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.repair"));
            return true;
        }

        // /repair all
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("aircore.command.repair.all")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.repair.all"));
                return true;
            }

            plugin.scheduler().runEntityTask(player, () -> {
                boolean repairedAny = false;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item == null || item.getType().isAir()) continue;
                    if (item.getType().getMaxDurability() <= 0) continue;
                    if (!(item.getItemMeta() instanceof Damageable damageable)) continue;

                    if (damageable.getDamage() > 0) {
                        damageable.setDamage(0);
                        item.setItemMeta(damageable);
                        repairedAny = true;
                    }
                }

                if (repairedAny) {
                    MessageUtil.send(player, "utilities.repair.inventory-repaired", Map.of());
                } else {
                    MessageUtil.send(player, "utilities.repair.inventory-not-damaged", Map.of());
                }
            });
            return true;
        }

        // /repair (single item in hand)
        plugin.scheduler().runEntityTask(player, () -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                MessageUtil.send(player, "utilities.repair.invalid-item", Map.of());
                return;
            }

            if (item.getType().getMaxDurability() <= 0) {
                MessageUtil.send(player, "utilities.repair.cannot-repair", Map.of());
                return;
            }

            if (!(item.getItemMeta() instanceof Damageable damageable)) {
                MessageUtil.send(player, "utilities.repair.cannot-repair", Map.of());
                return;
            }

            int currentDamage = damageable.getDamage();
            if (currentDamage > 0) {
                damageable.setDamage(0);
                item.setItemMeta(damageable);
                MessageUtil.send(player, "utilities.repair.item-repaired", Map.of());
            } else {
                MessageUtil.send(player, "utilities.repair.item-not-damaged", Map.of());
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1 && sender instanceof Player player) {
            if (player.hasPermission("aircore.command.repair.all")) {
                List<String> suggestions = new ArrayList<>();
                if ("all".startsWith(args[0].toLowerCase())) {
                    suggestions.add("all");
                }
                return suggestions;
            }
        }
        return List.of();
    }
}