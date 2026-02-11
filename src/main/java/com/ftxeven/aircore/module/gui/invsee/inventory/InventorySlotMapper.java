package com.ftxeven.aircore.module.gui.invsee.inventory;

import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.ItemComponent;
import com.ftxeven.aircore.database.player.PlayerInventories;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InventorySlotMapper {
    private static final MiniMessage MM = MiniMessage.miniMessage();

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
        for (int i = 0; i < slots.size() && (i + sourceOffset) < source.length; i++) {
            ItemStack item = source[i + sourceOffset];
            if (item != null) inv.setItem(slots.get(i), item);
        }
    }

    public static void fillCustom(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders, InventoryManager manager) {
        String viewerName = viewer.getName();
        String targetName = placeholders.getOrDefault("target", "");

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            if (manager.isDynamicGroup(key)) continue;

            GuiDefinition.GuiItem itemDef = entry.getValue();
            ItemStack customItem = buildCustomItem(def, key, itemDef, viewer, viewerName, targetName, placeholders);

            for (int slot : itemDef.slots()) {
                if (slot >= inv.getSize()) continue;
                ItemStack current = inv.getItem(slot);

                // Don't overwrite dynamic items (actual player items) with fillers
                if (isDynamicSlot(def, slot) && current != null && !current.getType().isAir()) {
                    continue;
                }
                inv.setItem(slot, customItem.clone());
            }
        }
    }

    private static ItemStack buildCustomItem(GuiDefinition def, String key, GuiDefinition.GuiItem itemDef, Player viewer, String vName, String tName, Map<String, String> ph) {
        Material material = resolveMaterial(def, key, viewer, ph);

        // Handle Head Owner
        String headOwner = itemDef.headOwner();
        if (headOwner != null && !headOwner.isBlank()) {
            headOwner = PlaceholderUtil.apply(viewer, headOwner.replace("%player%", vName).replace("%target%", tName));
        }

        // Handle Name
        Component name = null;
        String rawName = def.config().getString("items." + key + ".display-name");
        if (rawName != null) {
            name = MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, rawName.replace("%player%", vName).replace("%target%", tName)));
        }

        // Handle Lore
        List<String> rawLore = def.config().getStringList("items." + key + ".lore");
        List<Component> lore = rawLore.isEmpty() ? null : rawLore.stream()
                .map(line -> MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, line.replace("%player%", vName).replace("%target%", tName))))
                .toList();

        return new ItemComponent(material)
                .amount(Objects.requireNonNullElse(itemDef.amount(), 1))
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
    }

    private static Material resolveMaterial(GuiDefinition def, String itemKey, Player viewer, Map<String, String> ph) {
        String matStr = def.config().getString("items." + itemKey + ".material", "");
        if (matStr.startsWith("head-")) {
            String part = matStr.substring(5).replace("%player%", viewer.getName()).replace("%target%", ph.getOrDefault("target", ""));
            if (!part.isBlank() && !part.contains("%")) return Material.PLAYER_HEAD;
        }
        return def.items().get(itemKey) != null ? def.items().get(itemKey).material() : Material.STONE;
    }

    public static PlayerInventories.InventoryBundle extractBundle(Inventory inv, GuiDefinition def) {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        // Extract Hotbar (0-8)
        extractToBuffer(inv, def.items().get("player-hotbar").slots(), contents, 0, def);
        // Extract Inventory (9-35)
        extractToBuffer(inv, def.items().get("player-inventory").slots(), contents, 9, def);
        // Extract Armor
        extractToBuffer(inv, def.items().get("player-armor").slots(), armor, 0, def);

        // Extract Offhand
        List<Integer> ohSlots = def.items().get("player-offhand").slots();
        if (!ohSlots.isEmpty()) {
            ItemStack stack = inv.getItem(ohSlots.getFirst());
            if (!isCustomFillerAt(def, ohSlots.getFirst(), stack)) offhand = stack;
        }

        return new PlayerInventories.InventoryBundle(contents, armor, offhand, new ItemStack[27]);
    }

    private static void extractToBuffer(Inventory inv, List<Integer> slots, ItemStack[] buffer, int offset, GuiDefinition def) {
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
        if (current == null || current.getType().isAir() || !isDynamicSlot(def, slot)) return false;
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