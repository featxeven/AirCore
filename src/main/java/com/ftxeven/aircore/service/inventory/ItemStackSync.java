package com.ftxeven.aircore.service.inventory;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

final class ItemStackSync {

    private ItemStackSync() {
    }

    static boolean matches(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aEmpty = isEmpty(a);
        boolean bEmpty = isEmpty(b);
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    static @Nullable ItemStack clone(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }
}