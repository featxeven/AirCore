package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.dao.PlayerRecords;
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
    private static final MiniMessage MM = MiniMessage.miniMessage();

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

        public GuiItem applyOverride(ConfigurationSection sec) {
            if (sec == null) return this;

            Material mat = this.material;
            String head = this.headOwner;
            String matStr = sec.getString("material");

            if (matStr != null) {
                if (matStr.startsWith("head-")) {
                    mat = Material.PLAYER_HEAD;
                    head = matStr.substring(5);
                } else {
                    mat = Material.matchMaterial(matStr);
                }
            }

            return new GuiItem(
                    this.key,
                    this.slots,
                    mat != null ? mat : this.material,
                    sec.getString("display-name", this.rawName),
                    sec.contains("lore") ? sec.getStringList("lore") : this.rawLore,
                    sec.get("glow") instanceof Boolean b ? b : this.glow,
                    sec.getString("item-model", this.itemModel),
                    sec.contains("actions") ? sec.getStringList("actions") : this.actions,
                    sec.contains("left-actions") ? sec.getStringList("left-actions") : this.leftActions,
                    sec.contains("right-actions") ? sec.getStringList("right-actions") : this.rightActions,
                    sec.contains("shift-actions") ? sec.getStringList("shift-actions") : this.shiftActions,
                    sec.contains("shift-left-actions") ? sec.getStringList("shift-left-actions") : this.shiftLeftActions,
                    sec.contains("shift-right-actions") ? sec.getStringList("shift-right-actions") : this.shiftRightActions,
                    sec.get("amount") instanceof Integer i ? i : this.amount,
                    sec.get("custom-model-data") instanceof Integer i ? i : this.customModelData,
                    sec.get("damage") instanceof Integer i ? i : this.damage,
                    this.enchants,
                    this.flags,
                    head,
                    sec.get("hide-tooltip") instanceof Boolean b ? b : this.hideTooltip,
                    sec.getString("tooltip-style", this.tooltipStyle),
                    sec.get("cooldown") instanceof Number n ? n.doubleValue() : this.cooldown,
                    sec.getString("cooldown-message", this.cooldownMessage)
            );
        }

        public static GuiItem fromSection(String key, ConfigurationSection sec) {
            String matStr = sec.getString("material", "STONE");
            Material mat = Material.matchMaterial(matStr.startsWith("head-") ? "PLAYER_HEAD" : matStr);
            String headOwner = matStr.startsWith("head-") ? matStr.substring(5) : null;

            return new GuiItem(
                    key,
                    parseSlots(sec.getStringList("slots")),
                    mat == null ? Material.STONE : mat,
                    sec.getString("display-name"),
                    sec.getStringList("lore"),
                    sec.get("glow") instanceof Boolean b ? b : false,
                    sec.getString("item-model"),
                    sec.getStringList("actions"),
                    sec.getStringList("left-actions"),
                    sec.getStringList("right-actions"),
                    sec.getStringList("shift-actions"),
                    sec.getStringList("shift-left-actions"),
                    sec.getStringList("shift-right-actions"),
                    sec.get("amount") instanceof Integer i ? i : null,
                    sec.get("custom-model-data") instanceof Integer i ? i : null,
                    sec.get("damage") instanceof Integer i ? i : null,
                    Map.of(),
                    List.of(),
                    headOwner,
                    sec.get("hide-tooltip") instanceof Boolean b ? b : null,
                    sec.getString("tooltip-style"),
                    sec.get("cooldown") instanceof Number n ? n.doubleValue() : 0.0,
                    sec.getString("cooldown-message")
            );
        }

        public ItemStack buildStack(Player viewer, Map<String, String> placeholders, AirCore plugin) {
            ItemComponent builder = new ItemComponent(this.material);

            if (this.rawName != null) {
                builder.name(MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, this.rawName, placeholders)));
            }

            if (this.rawLore != null && !this.rawLore.isEmpty()) {
                List<Component> processedLore = new ArrayList<>(this.rawLore.size());
                for (String line : this.rawLore) {
                    String applied = PlaceholderUtil.apply(viewer, line, placeholders);
                    if (applied.contains("\n")) {
                        for (String split : applied.split("\n")) {
                            processedLore.add(MM.deserialize("<!italic>" + split));
                        }
                    } else {
                        processedLore.add(MM.deserialize("<!italic>" + applied));
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
                    .hideTooltip(this.hideTooltip != null && this.hideTooltip)
                    .tooltipStyle(this.tooltipStyle)
                    .itemModel(this.itemModel);

            if (this.headOwner != null) {
                String processedOwner = PlaceholderUtil.apply(viewer, this.headOwner, placeholders);

                UUID ownerUuid = plugin.database().records().uuidFromName(processedOwner);

                PlayerRecords.SkinData skin = (ownerUuid != null)
                        ? plugin.database().records().getSkinData(ownerUuid)
                        : null;

                builder.skullOwner(processedOwner, viewer, skin);
            }

            return builder.build();
        }
    }
}