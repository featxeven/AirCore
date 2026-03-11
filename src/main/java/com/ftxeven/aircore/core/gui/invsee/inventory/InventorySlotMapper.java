package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.database.dao.PlayerInventories;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InventorySlotMapper {

    private static final Set<String> DYNAMIC_GROUPS = Set.of(
            "hotbar-slots", "inventory-slots", "armor-slots", "offhand-slots"
    );

    private InventorySlotMapper() {}

    public static void fill(AirCore plugin, Inventory inv, GuiDefinition def, PlayerInventories.InventoryBundle bundle, Player viewer, Map<String, String> ph) {
        mapToSlots(plugin, inv, def, getSlots(def, "hotbar-slots"), bundle.contents(), 0, viewer, ph);
        mapToSlots(plugin, inv, def, getSlots(def, "inventory-slots"), bundle.contents(), 9, viewer, ph);
        mapToSlots(plugin, inv, def, getSlots(def, "armor-slots"), bundle.armor(), 0, viewer, ph);

        List<Integer> offhandSlots = getSlots(def, "offhand-slots");
        if (offhandSlots != null && !offhandSlots.isEmpty()) {
            updateSlot(plugin, inv, def, offhandSlots.getFirst(), bundle.offhand(), viewer, ph);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> ph, InventoryManager mgr, AirCore plugin) {
        for (GuiItem item : def.items().values()) {
            if (mgr.isDynamicGroup(item.key())) continue;

            ItemStack stack = item.buildStack(viewer, ph, plugin);
            for (int slot : item.slots()) {
                if (slot < inv.getSize()) {
                    ItemStack current = inv.getItem(slot);
                    if (current == null || !current.isSimilar(stack)) {
                        inv.setItem(slot, stack);
                    }
                }
            }
        }
    }

    public static PlayerInventories.InventoryBundle extractBundle(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];

        mapToBuffer(inv, getSlots(def, "hotbar-slots"), contents, 0, def);
        mapToBuffer(inv, getSlots(def, "inventory-slots"), contents, 9, def);
        mapToBuffer(inv, getSlots(def, "armor-slots"), armor, 0, def);

        List<Integer> ohSlots = getSlots(def, "offhand-slots");
        ItemStack offhand = (ohSlots != null && !ohSlots.isEmpty()) ? getValidItem(inv, def, ohSlots.getFirst()) : null;

        return new PlayerInventories.InventoryBundle(contents, armor, offhand, null);
    }

    private static void mapToBuffer(Inventory inv, List<Integer> slots, ItemStack[] buffer, int offset, GuiDefinition def) {
        if (slots == null) return;
        for (int i = 0; i < slots.size() && (i + offset) < buffer.length; i++) {
            int guiSlot = slots.get(i);
            if (guiSlot >= inv.getSize()) continue;

            ItemStack item = inv.getItem(guiSlot);
            buffer[i + offset] = isCustomFillerAt(def, guiSlot, item) ? null : item;
        }
    }

    private static void mapToSlots(AirCore plugin, Inventory inv, GuiDefinition def, List<Integer> slots, ItemStack[] source, int offset, Player viewer, Map<String, String> ph) {
        if (slots == null) return;
        for (int i = 0; i < slots.size() && (i + offset) < source.length; i++) {
            updateSlot(plugin, inv, def, slots.get(i), source[i + offset], viewer, ph);
        }
    }

    private static void updateSlot(AirCore plugin, Inventory inv, GuiDefinition def, int slot, ItemStack newItem, Player viewer, Map<String, String> ph) {
        if (newItem != null && !newItem.getType().isAir()) {
            inv.setItem(slot, newItem);
            return;
        }

        GuiItem filler = findCustomItemAt(def, slot);
        if (filler != null) {
            ItemStack fillerStack = filler.buildStack(viewer, ph, plugin);
            ItemStack current = inv.getItem(slot);
            if (current == null || !current.isSimilar(fillerStack)) {
                inv.setItem(slot, fillerStack);
            }
        } else {
            inv.setItem(slot, null);
        }
    }

    private static ItemStack getValidItem(Inventory inv, GuiDefinition def, int slot) {
        ItemStack stack = inv.getItem(slot);
        return isCustomFillerAt(def, slot, stack) ? null : stack;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        for (String group : DYNAMIC_GROUPS) {
            GuiItem item = def.items().get(group);
            if (item != null && item.slots().contains(slot)) return true;
        }
        return false;
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        GuiItem match = findCustomItemAt(def, slot);
        return match != null && current.getType().name().equalsIgnoreCase(String.valueOf(match.material()));
    }

    public static GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (GuiItem item : def.items().values()) {
            if (!item.key().endsWith("-slots") && item.slots().contains(slot)) {
                return item;
            }
        }
        return null;
    }

    public static GuiItem findItem(GuiDefinition def, int slot) {
        for (GuiItem item : def.items().values()) {
            if (item.slots().contains(slot)) return item;
        }
        return null;
    }

    private static List<Integer> getSlots(GuiDefinition def, String key) {
        GuiItem item = def.items().get(key);
        return item != null ? item.slots() : null;
    }
}