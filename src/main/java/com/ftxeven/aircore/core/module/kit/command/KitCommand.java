package com.ftxeven.aircore.core.module.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.dao.PlayerKits;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class KitCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.kit";
    private static final String PERM_ALL = "aircore.command.kit.*";
    private static final String BYPASS_COOLDOWN = "aircore.bypass.kit.cooldown";
    private static final String BYPASS_ONETIME = "aircore.bypass.kit.onetime";

    public KitCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        String usage = plugin.commandConfig().getUsage("kit", label);

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        String kitName = args[0].toLowerCase();
        if (!plugin.kit().kits().exists(kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of("name", kitName));
            return true;
        }

        if (!player.hasPermission(PERM_ALL) && !player.hasPermission(PERM_BASE + "." + kitName)) {
            MessageUtil.send(player, "kits.usage.no-permission", Map.of("name", kitName));
            return true;
        }

        plugin.scheduler().runEntityTask(player, () -> {
            PlayerKits.KitData data = plugin.database().kits().load(player.getUniqueId(), kitName);
            long now = System.currentTimeMillis() / 1000;
            var config = plugin.kit().kits().getConfig();
            String path = "kits." + kitName;

            long configuredCooldown = config.getLong(path + ".cooldown", 0);
            boolean oneTime = config.getBoolean(path + ".one-time", false);
            boolean autoEquip = config.getBoolean(path + ".auto-equip", false);

            long activeCooldown = data.lastCooldown() > 0 ? data.lastCooldown() : configuredCooldown;
            if (activeCooldown > 0 && (now - data.lastClaim()) < activeCooldown) {
                if (!player.hasPermission(BYPASS_COOLDOWN)) {
                    long remaining = activeCooldown - (now - data.lastClaim());
                    MessageUtil.send(player, "kits.usage.cooldown", Map.of("name", kitName, "time", TimeUtil.formatSeconds(plugin, remaining)));
                    return;
                }
            }

            if (oneTime && data.oneTimeClaimed() && !player.hasPermission(BYPASS_ONETIME)) {
                MessageUtil.send(player, "kits.errors.cannot-claim-again", Map.of("name", kitName));
                return;
            }

            List<ItemStack> items = plugin.kit().kits().getKitItems(kitName);
            if (items.isEmpty()) {
                MessageUtil.send(player, "kits.errors.empty-kit", Map.of("name", kitName));
                return;
            }

            boolean dropWhenFull = plugin.config().kitsDropItemsWhenFull();
            if (!dropWhenFull && !canFitAll(player, items, autoEquip)) {
                MessageUtil.send(player, "kits.usage.inventory-full", Map.of("name", kitName));
                return;
            }

            for (ItemStack item : items) {
                ItemStack toAdd = item.clone();
                boolean equipped = false;

                if (autoEquip) {
                    equipped = plugin.kit().kits().tryEquipExternal(player, toAdd);
                }

                if (!equipped) {
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toAdd);
                    if (!leftovers.isEmpty() && dropWhenFull) {
                        leftovers.values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
                    }
                }
            }

            plugin.database().kits().save(player.getUniqueId(), kitName, now, oneTime, configuredCooldown);
            MessageUtil.send(player, "kits.usage.claimed", Map.of("name", kitName));
        });

        return true;
    }

    private boolean canFitAll(Player player, List<ItemStack> incoming, boolean autoEquip) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        ItemStack[] simulated = Arrays.stream(storage).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);

        for (ItemStack item : incoming) {
            if (item == null || item.getType() == Material.AIR) continue;
            ItemStack toAdd = item.clone();

            if (autoEquip) {
                var slot = plugin.kit().kits().resolveEquipmentSlotExternal(toAdd);
                if (plugin.kit().kits().isSlotEmptyExternal(player, slot)) continue;
            }

            int remaining = toAdd.getAmount();
            for (ItemStack slotItem : simulated) {
                if (slotItem != null && slotItem.isSimilar(toAdd)) {
                    int space = slotItem.getMaxStackSize() - slotItem.getAmount();
                    remaining -= Math.min(space, remaining);
                }
                if (remaining <= 0) break;
            }

            if (remaining > 0) {
                for (int i = 0; i < simulated.length; i++) {
                    if (simulated[i] == null || simulated[i].getType() == Material.AIR) {
                        remaining -= Math.min(toAdd.getMaxStackSize(), remaining);
                        simulated[i] = toAdd.clone();
                    }
                    if (remaining <= 0) break;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERM_BASE) || args.length != 1) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        var section = plugin.kit().kits().getConfig().getConfigurationSection("kits");
        if (section == null) return Collections.emptyList();

        return section.getKeys(false).stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .filter(name -> player.hasPermission(PERM_ALL) || player.hasPermission(PERM_BASE + "." + name.toLowerCase()))
                .limit(20)
                .toList();
    }
}