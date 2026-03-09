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
import java.util.stream.Stream;

public final class InventorySlotMapper {

    private static final String[] DYNAMIC_GROUPS = {"hotbar-slots", "inventory-slots", "armor-slots", "offhand-slots"};

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

            item.slots().stream().filter(s -> s < inv.getSize()).forEach(slot -> {
                ItemStack current = inv.getItem(slot);
                if (current == null || !current.isSimilar(stack)) {
                    inv.setItem(slot, stack);
                }
            });
        }
    }

    public static PlayerInventories.InventoryBundle extractBundle(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];

        extractToBuffer(inv, getSlots(def, "hotbar-slots"), contents, 0, def);
        extractToBuffer(inv, getSlots(def, "inventory-slots"), contents, 9, def);
        extractToBuffer(inv, getSlots(def, "armor-slots"), armor, 0, def);

        List<Integer> ohSlots = getSlots(def, "offhand-slots");
        ItemStack offhand = (ohSlots != null && !ohSlots.isEmpty()) ?
                getValidItem(inv, def, ohSlots.getFirst()) : null;

        return new PlayerInventories.InventoryBundle(contents, armor, offhand, null);
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
        } else {
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
    }

    private static void extractToBuffer(Inventory inv, List<Integer> slots, ItemStack[] buffer, int offset, GuiDefinition def) {
        if (slots == null) return;
        for (int i = 0; i < slots.size() && (i + offset) < buffer.length; i++) {
            buffer[i + offset] = getValidItem(inv, def, slots.get(i));
        }
    }

    private static ItemStack getValidItem(Inventory inv, GuiDefinition def, int slot) {
        ItemStack stack = inv.getItem(slot);
        return isCustomFillerAt(def, slot, stack) ? null : stack;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        return Stream.of(DYNAMIC_GROUPS)
                .map(group -> def.items().get(group))
                .anyMatch(item -> item != null && item.slots().contains(slot));
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        GuiItem match = findCustomItemAt(def, slot);
        return match != null && current.getType() == match.material();
    }

    public static GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        return def.items().values().stream()
                .filter(i -> !i.key().endsWith("-slots") && i.slots().contains(slot))
                .findFirst().orElse(null);
    }

    public static GuiItem findItem(GuiDefinition def, int slot) {
        return def.items().values().stream()
                .filter(i -> i.slots().contains(slot))
                .findFirst().orElse(null);
    }

    private static List<Integer> getSlots(GuiDefinition def, String key) {
        GuiItem item = def.items().get(key);
        return item != null ? item.slots() : null;
    }
}