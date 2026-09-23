package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.core.gui.GuiSession;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;

public final class ContainerRenderer {

    private ContainerRenderer() {
    }

    public static void draw(GuiSession session, ItemStack[] contents, Set<Integer> slots) {
        Inventory inventory = session.inventory();
        Iterator<Integer> slotIterator = slots.iterator();
        for (ItemStack item : contents) {
            if (!slotIterator.hasNext()) {
                break;
            }
            int slot = slotIterator.next();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ItemStack resolved = copyOf(item);
            if (resolved != null) {
                inventory.setItem(slot, resolved);
                session.claim(slot);
            }
        }
    }

    public static void draw(GuiSession session, @Nullable ItemStack item, int slot) {
        Inventory inventory = session.inventory();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack resolved = copyOf(item);
        if (resolved != null) {
            inventory.setItem(slot, resolved);
            session.claim(slot);
        }
    }

    public static void draw(GuiSession session, @Nullable ItemStack item, Set<Integer> slots) {
        for (int slot : slots) {
            draw(session, item, slot);
        }
    }

    private static @Nullable ItemStack copyOf(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir() ? item.clone() : null;
    }
}