package com.ftxeven.aircore.module.gui.invsee;

import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.ItemComponent;
import com.ftxeven.aircore.database.player.PlayerInventories;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InvseeSlotMapper {
    private InvseeSlotMapper() {}

    public static void fill(Inventory inv, GuiDefinition def, PlayerInventories.InventoryBundle bundle) {
        // Hotbar
        List<Integer> hotbarSlots = def.items().get("player-hotbar").slots();
        for (int i = 0; i < bundle.contents().length && i < hotbarSlots.size(); i++) {
            ItemStack item = bundle.contents()[i];
            if (item != null) inv.setItem(hotbarSlots.get(i), item);
        }

        // Armor
        List<Integer> armorSlots = def.items().get("player-armor").slots();
        ItemStack[] armor = bundle.armor();
        for (int i = 0; i < armor.length && i < armorSlots.size(); i++) {
            if (armor[i] != null) inv.setItem(armorSlots.get(i), armor[i]);
        }

        // Offhand
        List<Integer> offhandSlots = def.items().get("player-offhand").slots();
        if (bundle.offhand() != null && !offhandSlots.isEmpty()) {
            inv.setItem(offhandSlots.getFirst(), bundle.offhand());
        }

        // Contents (main inventory)
        List<Integer> invSlots = def.items().get("player-inventory").slots();
        for (int i = 9; i < bundle.contents().length && i - 9 < invSlots.size(); i++) {
            ItemStack item = bundle.contents()[i];
            if (item != null) inv.setItem(invSlots.get(i - 9), item);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String,String> placeholders, InvseeManager manager) {
        MiniMessage mm = MiniMessage.miniMessage();

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            GuiDefinition.GuiItem itemDef = entry.getValue();

            if (manager.isDynamicGroup(key)) continue;

            for (int slot : itemDef.slots()) {
                ItemStack currentItem = inv.getItem(slot);

                boolean isDynamic = isDynamicSlot(def, slot);
                if (isDynamic && currentItem != null && !currentItem.getType().isAir()) {
                    continue;
                }

                Component name = null;
                if (itemDef.displayName() != null) {
                    String raw = mm.serialize(itemDef.displayName());
                    raw = PlaceholderUtil.apply(viewer, raw);
                    raw = raw.replace("%player%", viewer.getName())
                            .replace("%target%", placeholders.get("target"));
                    name = mm.deserialize(raw);
                }

                List<Component> lore = null;
                if (itemDef.lore() != null && !itemDef.lore().isEmpty()) {
                    lore = new ArrayList<>(itemDef.lore().size());
                    for (Component comp : itemDef.lore()) {
                        String raw = mm.serialize(comp);
                        raw = PlaceholderUtil.apply(viewer, raw);
                        raw = raw.replace("%player%", viewer.getName())
                                .replace("%target%", placeholders.get("target"));
                        lore.add(mm.deserialize(raw));
                    }
                }

                ItemStack custom = new ItemComponent(itemDef.material())
                        .amount(itemDef.amount() != null ? itemDef.amount() : 1)
                        .name(name)
                        .lore(lore)
                        .glow(itemDef.glow())
                        .itemModel(itemDef.itemModel())
                        .customModelData(itemDef.customModelData())
                        .damage(itemDef.damage())
                        .enchants(itemDef.enchants())
                        .flags(itemDef.flags() != null ? itemDef.flags().toArray(new ItemFlag[0]) : new ItemFlag[0])
                        .skullOwner(itemDef.skullOwner())
                        .hideTooltip(itemDef.hideTooltip())
                        .tooltipStyle(itemDef.tooltipStyle())
                        .build();

                inv.setItem(slot, custom);
            }
        }
    }

    public static GuiDefinition.GuiItem findItem(GuiDefinition def, int slot) {
        for (GuiDefinition.GuiItem item : def.items().values()) {
            if (item.slots().contains(slot)) return item;
        }
        return null;
    }

    public static PlayerInventories.InventoryBundle extractBundle(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[PlayerInventories.CONTENTS_SIZE];
        List<Integer> hotbarSlots = def.items().get("player-hotbar").slots();
        for (int i = 0; i < hotbarSlots.size() && i < contents.length; i++) {
            ItemStack stack = inv.getItem(hotbarSlots.get(i));
            contents[i] = isCustomFillerAt(def, hotbarSlots.get(i), stack) ? null : stack;
        }

        List<Integer> invSlots = def.items().get("player-inventory").slots();
        for (int i = 0; i < invSlots.size() && i + 9 < contents.length; i++) {
            ItemStack stack = inv.getItem(invSlots.get(i));
            contents[i + 9] = isCustomFillerAt(def, invSlots.get(i), stack) ? null : stack;
        }

        ItemStack[] armor = new ItemStack[PlayerInventories.ARMOR_SIZE];
        List<Integer> armorSlots = def.items().get("player-armor").slots();
        for (int i = 0; i < armorSlots.size() && i < armor.length; i++) {
            ItemStack stack = inv.getItem(armorSlots.get(i));
            armor[i] = isCustomFillerAt(def, armorSlots.get(i), stack) ? null : stack;
        }

        ItemStack offhand = null;
        List<Integer> offhandSlots = def.items().get("player-offhand").slots();
        if (!offhandSlots.isEmpty()) {
            ItemStack stack = inv.getItem(offhandSlots.getFirst());
            if (!isCustomFillerAt(def, offhandSlots.getFirst(), stack)) {
                offhand = stack;
            }
        }

        return new PlayerInventories.InventoryBundle(contents, armor, offhand,
                new ItemStack[PlayerInventories.ENDERCHEST_SIZE]);
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        GuiDefinition.GuiItem hotbar = def.items().get("player-hotbar");
        GuiDefinition.GuiItem contents = def.items().get("player-inventory");
        GuiDefinition.GuiItem armor = def.items().get("player-armor");
        GuiDefinition.GuiItem offhand = def.items().get("player-offhand");

        return (hotbar != null && hotbar.slots().contains(slot))
                || (contents != null && contents.slots().contains(slot))
                || (armor != null && armor.slots().contains(slot))
                || (offhand != null && offhand.slots().contains(slot));
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;

        if (!isDynamicSlot(def, slot)) return false;

        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            GuiDefinition.GuiItem gi = e.getValue();

            if (key.startsWith("player-")) continue; // skip dynamic groups
            if (!gi.slots().contains(slot)) continue;

            // Check if material matches
            if (current.getType() == gi.material()) return true;
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            GuiDefinition.GuiItem gi = e.getValue();
            if (key.startsWith("player-")) continue; // skip dynamic groups
            if (gi.slots().contains(slot)) return gi;
        }
        return null;
    }
}