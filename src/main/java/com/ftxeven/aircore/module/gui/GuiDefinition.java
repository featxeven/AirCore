package com.ftxeven.aircore.module.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record GuiDefinition(String title,
                            int rows,
                            Map<String, GuiItem> items,
                            YamlConfiguration config) {

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public static List<Integer> parseSlots(List<String> rawSlots) {
        List<Integer> result = new ArrayList<>();
        for (String raw : rawSlots) {
            if (raw == null || raw.isBlank()) continue;
            raw = raw.trim();
            if (raw.contains("-")) {
                String[] parts = raw.split("-");
                if (parts.length == 2) {
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        if (start <= end) {
                            for (int i = start; i <= end; i++) {
                                result.add(i);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    result.add(Integer.parseInt(raw));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public record GuiItem(String key,
                          List<Integer> slots,
                          Material material,
                          Component displayName,
                          List<Component> lore,
                          boolean glow,
                          String itemModel,
                          List<String> actions,
                          List<String> leftActions,
                          List<String> rightActions,
                          List<String> shiftLeftActions,
                          List<String> shiftRightActions,
                          Integer amount,
                          Integer customModelData,
                          Integer damage,
                          Map<String, Integer> enchants,
                          List<ItemFlag> flags,
                          String skullOwner,
                          Boolean hideTooltip,
                          String tooltipStyle) {

        public List<String> getActionsForClick(ClickType click) {
            if (click == ClickType.SHIFT_LEFT) {
                if (shiftLeftActions != null && !shiftLeftActions.isEmpty()) {
                    return shiftLeftActions;
                }
            } else if (click == ClickType.SHIFT_RIGHT) {
                if (shiftRightActions != null && !shiftRightActions.isEmpty()) {
                    return shiftRightActions;
                }
            } else if (click == ClickType.LEFT) {
                if (leftActions != null && !leftActions.isEmpty()) {
                    return leftActions;
                }
            } else if (click == ClickType.RIGHT) {
                if (rightActions != null && !rightActions.isEmpty()) {
                    return rightActions;
                }
            }

            return actions;
        }

        public static GuiItem fromSection(String key, ConfigurationSection sec, MiniMessage mm) {
            Material material = Material.matchMaterial(sec.getString("material", "STONE"));
            if (material == null) material = Material.STONE;

            String displayName = sec.getString("display-name");
            Component name = displayName != null && !displayName.isBlank()
                    ? mm.deserialize(displayName).decoration(TextDecoration.ITALIC, false)
                    : null;

            List<Component> lore = new ArrayList<>();
            for (String s : sec.getStringList("lore")) {
                lore.add(mm.deserialize(s).decoration(TextDecoration.ITALIC, false));
            }

            List<Integer> slots = GuiDefinition.parseSlots(sec.getStringList("slots"));

            return new GuiItem(
                    key,
                    slots,
                    material,
                    name,
                    lore,
                    sec.getBoolean("glow", false),
                    sec.getString("item-model"),
                    sec.getStringList("actions"),
                    sec.getStringList("left-actions"),
                    sec.getStringList("right-actions"),
                    sec.getStringList("shift-left-actions"),
                    sec.getStringList("shift-right-actions"),
                    sec.contains("amount") ? sec.getInt("amount") : null,
                    sec.contains("custom-model-data") ? sec.getInt("custom-model-data") : null,
                    sec.contains("damage") ? sec.getInt("damage") : null,
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    sec.getString("skull-owner"),
                    sec.contains("hide-tooltip") ? sec.getBoolean("hide-tooltip") : null,
                    sec.getString("tooltip-style")
            );
        }
    }
}
