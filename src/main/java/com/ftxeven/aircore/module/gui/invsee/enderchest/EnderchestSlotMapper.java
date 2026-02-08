package com.ftxeven.aircore.module.gui.invsee.enderchest;

import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.ItemComponent;
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

public final class EnderchestSlotMapper {
    private EnderchestSlotMapper() {
    }

    public static void fill(Inventory inv, GuiDefinition def, ItemStack[] contents) {
        List<Integer> slots = def.items().get("player-enderchest").slots();
        for (int i = 0; i < contents.length && i < slots.size(); i++) {
            ItemStack item = contents[i];
            if (item != null) inv.setItem(slots.get(i), item);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders, EnderchestManager manager) {
        MiniMessage mm = MiniMessage.miniMessage();

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            GuiDefinition.GuiItem itemDef = entry.getValue();

            // Skip dynamic groups
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
                    raw = raw.replace("%player%", viewer.getName())
                            .replace("%target%", placeholders.getOrDefault("target", ""));
                    raw = PlaceholderUtil.apply(viewer, raw);
                    name = mm.deserialize(raw);
                }

                List<Component> lore = null;
                if (itemDef.lore() != null && !itemDef.lore().isEmpty()) {
                    lore = new ArrayList<>(itemDef.lore().size());
                    for (Component comp : itemDef.lore()) {
                        String raw = mm.serialize(comp);
                        raw = raw.replace("%player%", viewer.getName())
                                .replace("%target%", placeholders.getOrDefault("target", ""));
                        raw = PlaceholderUtil.apply(viewer, raw);
                        lore.add(mm.deserialize(raw));
                    }
                }

                String headOwner = itemDef.headOwner();
                if (headOwner != null && !headOwner.isBlank()) {
                    headOwner = headOwner.replace("%player%", viewer.getName());
                    String targetName = placeholders.get("target");
                    if (targetName != null && !targetName.isBlank()) {
                        headOwner = headOwner.replace("%target%", targetName);
                    }

                    headOwner = PlaceholderUtil.apply(viewer, headOwner);

                    if (headOwner.isBlank()) {
                        headOwner = null;
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
                        .skullOwner(headOwner)
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

    public static ItemStack[] extractContents(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[27];

        List<Integer> invSlots = def.items().get("player-enderchest").slots();
        for (int i = 0; i < invSlots.size() && i < contents.length; i++) {
            ItemStack stack = inv.getItem(invSlots.get(i));
            contents[i] = isCustomFillerAt(def, invSlots.get(i), stack) ? null : stack;
        }

        return contents;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        GuiDefinition.GuiItem inventory = def.items().get("player-enderchest");
        return inventory != null && inventory.slots().contains(slot);
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;

        if (!isDynamicSlot(def, slot)) return false;

        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            GuiDefinition.GuiItem gi = e.getValue();

            if (key.startsWith("player-")) continue; // skip dynamic groups
            if (!gi.slots().contains(slot)) continue;

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