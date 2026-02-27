package com.ftxeven.aircore.core.modules.gui.invsee.enderchest;

import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class EnderchestSlotMapper {

    private EnderchestSlotMapper() {}

    public static void fill(Inventory inv, GuiDefinition def, ItemStack[] contents) {
        GuiItem ecGroup = def.items().get("enderchest-slots");
        if (ecGroup == null) return;

        List<Integer> slots = ecGroup.slots();
        for (int i = 0; i < slots.size(); i++) {
            inv.setItem(slots.get(i), (i < contents.length) ? contents[i] : null);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> ph, EnderchestManager mgr) {
        for (GuiItem item : def.items().values()) {
            if (mgr.isDynamicGroup(item.key())) continue;

            ItemStack stack = item.buildStack(viewer, ph);

            item.slots().stream().filter(s -> s < inv.getSize()).forEach(slot -> {
                if (isDynamicSlot(def, slot)) {
                    ItemStack existing = inv.getItem(slot);
                    if (existing != null && !existing.getType().isAir()) return;
                }

                ItemStack current = inv.getItem(slot);
                if (current == null || !current.isSimilar(stack)) {
                    inv.setItem(slot, stack);
                }
            });
        }
    }

    public static ItemStack[] extractContents(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[27];
        GuiItem ecGroup = def.items().get("enderchest-slots");
        if (ecGroup == null) return contents;

        List<Integer> slots = ecGroup.slots();
        for (int i = 0; i < Math.min(slots.size(), contents.length); i++) {
            ItemStack stack = inv.getItem(slots.get(i));
            contents[i] = isCustomFillerAt(def, slots.get(i), stack) ? null : stack;
        }
        return contents;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        GuiItem group = def.items().get("enderchest-slots");
        return group != null && group.slots().contains(slot);
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        GuiItem match = findCustomItemAt(def, slot);
        return match != null && current.getType() == match.material();
    }

    public static GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        return def.items().values().stream()
                .filter(i -> !i.key().startsWith("enderchest-slots") && i.slots().contains(slot))
                .findFirst().orElse(null);
    }

    public static GuiItem findItem(GuiDefinition def, int slot) {
        return def.items().values().stream()
                .filter(i -> i.slots().contains(slot))
                .findFirst().orElse(null);
    }
}