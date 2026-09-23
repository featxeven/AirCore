package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class RepairSupport {

    private RepairSupport() {
    }

    enum ItemVerdict {
        REPAIRED,
        NOT_DAMAGED,
        CANNOT_REPAIR,
        NO_ITEM
    }

    // /repair
    static ItemVerdict repairHeldItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItemInMainHand();

        ItemVerdict verdict = repair(item);
        if (verdict == ItemVerdict.REPAIRED) {
            inventory.setItemInMainHand(item);
        }
        return verdict;
    }

    // /repairall
    static int repairAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        int repaired = 0;

        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (repair(storage[i]) == ItemVerdict.REPAIRED) {
                repaired++;
            }
        }
        inventory.setStorageContents(storage);

        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (repair(armor[i]) == ItemVerdict.REPAIRED) {
                repaired++;
            }
        }
        inventory.setArmorContents(armor);

        ItemStack offHand = inventory.getItemInOffHand();
        if (repair(offHand) == ItemVerdict.REPAIRED) {
            repaired++;
            inventory.setItemInOffHand(offHand);
        }

        return repaired;
    }

    private static ItemVerdict repair(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return ItemVerdict.NO_ITEM;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return ItemVerdict.CANNOT_REPAIR;
        }
        if (damageable.getDamage() <= 0) {
            return ItemVerdict.NOT_DAMAGED;
        }
        damageable.setDamage(0);
        item.setItemMeta(meta);
        return ItemVerdict.REPAIRED;
    }
}