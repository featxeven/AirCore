package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.database.player.PlayerInventories;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class InventorySlotMapper {

    private InventorySlotMapper() {}

    public static void fill(Inventory inv, GuiDefinition def, PlayerInventories.InventoryBundle bundle) {
        // Hotbar (0-8)
        mapToSlots(inv, def.items().get("player-hotbar").slots(), bundle.contents(), 0);
        // Inventory (9-35)
        mapToSlots(inv, def.items().get("player-inventory").slots(), bundle.contents(), 9);
        // Armor
        mapToSlots(inv, def.items().get("player-armor").slots(), bundle.armor(), 0);

        // Offhand
        List<Integer> offhandSlots = def.items().get("player-offhand").slots();
        if (bundle.offhand() != null && !offhandSlots.isEmpty()) {
            inv.setItem(offhandSlots.getFirst(), bundle.offhand());
        }
    }

    private static void mapToSlots(Inventory inv, List<Integer> slots, ItemStack[] source, int sourceOffset) {
        if (slots == null) return;
        for (int i = 0; i < slots.size() && (i + sourceOffset) < source.length; i++) {
            ItemStack item = source[i + sourceOffset];
            if (item != null) inv.setItem(slots.get(i), item);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders, InventoryManager manager) {
        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            if (manager.isDynamicGroup(key)) continue;

            GuiDefinition.GuiItem itemDef = entry.getValue();

            ItemStack customItem = itemDef.buildStack(viewer, placeholders);

            for (int slot : itemDef.slots()) {
                if (slot >= inv.getSize()) continue;
                ItemStack current = inv.getItem(slot);

                if (isDynamicSlot(def, slot) && current != null && !current.getType().isAir()) {
                    continue;
                }
                inv.setItem(slot, customItem);
            }
        }
    }

    public static PlayerInventories.InventoryBundle extractBundle(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        extractToBuffer(inv, def.items().get("player-hotbar").slots(), contents, 0, def);
        extractToBuffer(inv, def.items().get("player-inventory").slots(), contents, 9, def);
        extractToBuffer(inv, def.items().get("player-armor").slots(), armor, 0, def);

        List<Integer> ohSlots = def.items().get("player-offhand").slots();
        if (!ohSlots.isEmpty()) {
            ItemStack stack = inv.getItem(ohSlots.getFirst());
            if (!isCustomFillerAt(def, ohSlots.getFirst(), stack)) offhand = stack;
        }

        return new PlayerInventories.InventoryBundle(contents, armor, offhand, new ItemStack[27]);
    }

    private static void extractToBuffer(Inventory inv, List<Integer> slots, ItemStack[] buffer, int offset, GuiDefinition def) {
        if (slots == null) return;
        for (int i = 0; i < slots.size() && (i + offset) < buffer.length; i++) {
            int slot = slots.get(i);
            ItemStack stack = inv.getItem(slot);
            buffer[i + offset] = isCustomFillerAt(def, slot, stack) ? null : stack;
        }
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        String[] groups = {"player-hotbar", "player-inventory", "player-armor", "player-offhand"};
        for (String g : groups) {
            GuiDefinition.GuiItem item = def.items().get(g);
            if (item != null && item.slots().contains(slot)) return true;
        }
        return false;
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            if (e.getKey().startsWith("player-")) continue;
            if (e.getValue().slots().contains(slot) && current.getType() == e.getValue().material()) return true;
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            if (!e.getKey().startsWith("player-") && e.getValue().slots().contains(slot)) return e.getValue();
        }
        return null;
    }

    public static GuiDefinition.GuiItem findItem(GuiDefinition def, int slot) {
        for (GuiDefinition.GuiItem item : def.items().values()) {
            if (item.slots().contains(slot)) return item;
        }
        return null;
    }
}