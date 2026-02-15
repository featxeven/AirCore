package com.ftxeven.aircore.core.gui.invsee.enderchest;

import com.ftxeven.aircore.core.gui.GuiDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class EnderchestSlotMapper {

    private EnderchestSlotMapper() {}

    public static void fill(Inventory inv, GuiDefinition def, ItemStack[] contents) {
        GuiDefinition.GuiItem ecGroup = def.items().get("player-enderchest");
        if (ecGroup == null) return;

        List<Integer> slots = ecGroup.slots();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            ItemStack item = (i < contents.length) ? contents[i] : null;
            inv.setItem(slot, item);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders, EnderchestManager manager) {
        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();

            if (manager.isDynamicGroup(key)) continue;

            GuiDefinition.GuiItem itemDef = entry.getValue();

            ItemStack customItem = itemDef.buildStack(viewer, placeholders);

            for (int slot : itemDef.slots()) {
                if (slot >= inv.getSize()) continue;

                if (isDynamicSlot(def, slot)) {
                    ItemStack existing = inv.getItem(slot);
                    if (existing != null && !existing.getType().isAir()) continue;
                }
                inv.setItem(slot, customItem);
            }
        }
    }

    public static ItemStack[] extractContents(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[27];
        GuiDefinition.GuiItem ecGroup = def.items().get("player-enderchest");
        if (ecGroup == null) return contents;

        List<Integer> slots = ecGroup.slots();
        for (int i = 0; i < slots.size() && i < contents.length; i++) {
            int slot = slots.get(i);
            ItemStack stack = inv.getItem(slot);

            contents[i] = isCustomFillerAt(def, slot, stack) ? null : stack;
        }
        return contents;
    }

    public static GuiDefinition.GuiItem findItem(GuiDefinition def, int slot) {
        for (GuiDefinition.GuiItem item : def.items().values()) {
            if (item.slots().contains(slot)) return item;
        }
        return null;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        GuiDefinition.GuiItem ecGroup = def.items().get("player-enderchest");
        return ecGroup != null && ecGroup.slots().contains(slot);
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            if (e.getKey().startsWith("player-")) continue;
            GuiDefinition.GuiItem gi = e.getValue();
            if (gi.slots().contains(slot) && current.getType() == gi.material()) return true;
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            if (e.getKey().startsWith("player-")) continue;
            if (e.getValue().slots().contains(slot)) return e.getValue();
        }
        return null;
    }
}