package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public record GuiDefinition(String title, int rows, Map<String, GuiItem> items, YamlConfiguration config) {

    public static List<Integer> parseSlots(List<String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            if (s.contains("-")) {
                String[] p = s.split("-");
                try {
                    int start = Integer.parseInt(p[0].trim()), end = Integer.parseInt(p[1].trim());
                    for (int i = start; i <= end; i++) result.add(i);
                } catch (NumberFormatException ignored) {}
            } else {
                try { result.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public record GuiItem(String key, List<Integer> slots, Material material, String rawName, List<String> rawLore,
                          boolean glow, String itemModel, List<String> actions, List<String> leftActions, List<String> rightActions,
                          List<String> shiftActions, List<String> shiftLeftActions, List<String> shiftRightActions,
                          Integer amount, Integer customModelData, Integer damage, Map<String, Integer> enchants,
                          List<ItemFlag> flags, String headOwner, Boolean hideTooltip, String tooltipStyle,
                          double cooldown, String cooldownMessage) {

        public List<String> getActionsForClick(ClickType click) {
            List<String> specific = switch (click) {
                case SHIFT_LEFT -> (shiftLeftActions != null && !shiftLeftActions.isEmpty()) ? shiftLeftActions : shiftActions;
                case SHIFT_RIGHT -> (shiftRightActions != null && !shiftRightActions.isEmpty()) ? shiftRightActions : shiftActions;
                case LEFT -> leftActions;
                case RIGHT -> rightActions;
                default -> null;
            };
            return (specific != null && !specific.isEmpty()) ? specific : actions;
        }

        public static GuiItem fromSection(String key, ConfigurationSection sec) {
            String matStr = sec.getString("material", "STONE");
            Material mat = Material.matchMaterial(matStr.startsWith("head-") ? "PLAYER_HEAD" : matStr);
            String headOwner = matStr.startsWith("head-") ? matStr.substring(5) : null;

            String nameRaw = sec.getString("display-name");
            List<String> loreRaw = sec.getStringList("lore");

            return new GuiItem(key, parseSlots(sec.getStringList("slots")), mat == null ? Material.STONE : mat,
                    nameRaw, loreRaw, sec.getBoolean("glow"), sec.getString("item-model"),
                    sec.getStringList("actions"), sec.getStringList("left-actions"), sec.getStringList("right-actions"),
                    sec.getStringList("shift-actions"), sec.getStringList("shift-left-actions"), sec.getStringList("shift-right-actions"),
                    val(sec, "amount"), val(sec, "custom-model-data"), val(sec, "damage"),
                    Map.of(), List.of(), headOwner,
                    sec.contains("hide-tooltip") ? sec.getBoolean("hide-tooltip") : null, sec.getString("tooltip-style"),
                    sec.getDouble("cooldown", 0.0), sec.getString("cooldown-message"));
        }

        public ItemStack buildStack(Player viewer, Map<String, String> placeholders) {
            MiniMessage mm = MiniMessage.miniMessage();
            ItemComponent builder = new ItemComponent(this.material);

            if (this.rawName != null) {
                builder.name(mm.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, this.rawName, placeholders)));
            }

            if (this.rawLore != null && !this.rawLore.isEmpty()) {
                List<Component> processedLore = new ArrayList<>(this.rawLore.size() + 2);
                for (String line : this.rawLore) {
                    String applied = PlaceholderUtil.apply(viewer, line, placeholders);

                    if (applied.contains("\n")) {
                        for (String split : applied.split("\n")) {
                            processedLore.add(mm.deserialize("<!italic>" + split));
                        }
                    } else {
                        processedLore.add(mm.deserialize("<!italic>" + applied));
                    }
                }
                builder.lore(processedLore);
            }

            builder.amount(this.amount != null ? this.amount : 1)
                    .customModelData(this.customModelData)
                    .damage(this.damage)
                    .enchants(this.enchants)
                    .glow(this.glow)
                    .flags(this.flags.toArray(new ItemFlag[0]))
                    .hideTooltip(this.hideTooltip)
                    .tooltipStyle(this.tooltipStyle)
                    .itemModel(this.itemModel);

            if (this.headOwner != null) {
                builder.skullOwner(PlaceholderUtil.apply(viewer, this.headOwner, placeholders));
            }

            return builder.build();
        }

        private static Integer val(ConfigurationSection s, String path) { return s.contains(path) ? s.getInt(path) : null; }
    }
}