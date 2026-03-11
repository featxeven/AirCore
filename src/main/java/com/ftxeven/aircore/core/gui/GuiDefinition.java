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

    public record GuiItem(String key, List<Integer> slots, String material, String rawName, List<String> rawLore,
                          boolean glow, String itemModel, List<String> actions, List<String> leftActions, List<String> rightActions,
                          List<String> shiftActions, List<String> shiftLeftActions, List<String> shiftRightActions,
                          Integer amount, Integer customModelData, Integer damage, Map<String, Integer> enchants,
                          List<ItemFlag> flags, String headOwner, Boolean hideTooltip, String tooltipStyle,
                          double cooldown, String cooldownMessage,
                          TreeMap<Integer, ItemPriority> priorities) {

        public List<String> getActionsForClick(Player viewer, Map<String, String> ph, ClickType click) {
            if (!priorities.isEmpty()) {
                for (ItemPriority p : priorities.values()) {
                    if (p.matches(viewer, ph)) {
                        return p.actions() != null ? p.actions() : Collections.emptyList();
                    }
                }
            }

            return switch (click) {
                case LEFT -> !leftActions.isEmpty() ? leftActions : actions;
                case RIGHT -> !rightActions.isEmpty() ? rightActions : actions;
                case SHIFT_LEFT -> !shiftLeftActions.isEmpty() ? shiftLeftActions : (!shiftActions.isEmpty() ? shiftActions : actions);
                case SHIFT_RIGHT -> !shiftRightActions.isEmpty() ? shiftRightActions : (!shiftActions.isEmpty() ? shiftActions : actions);
                default -> actions;
            };
        }

        public static GuiItem fromSection(String key, ConfigurationSection sec) {
            TreeMap<Integer, ItemPriority> priorities = new TreeMap<>();
            ConfigurationSection prioSec = sec.getConfigurationSection("priority");
            if (prioSec != null) {
                for (String pKey : prioSec.getKeys(false)) {
                    try {
                        int pLevel = Integer.parseInt(pKey);
                        priorities.put(pLevel, ItemPriority.fromSection(Objects.requireNonNull(prioSec.getConfigurationSection(pKey))));
                    } catch (NumberFormatException ignored) {}
                }
            }

            return new GuiItem(
                    key, parseSlots(sec.getStringList("slots")),
                    sec.getString("material", "STONE"),
                    sec.getString("display-name"), sec.getStringList("lore"),
                    sec.getBoolean("glow", false), sec.getString("item-model"),
                    sec.getStringList("actions"), sec.getStringList("left-actions"), sec.getStringList("right-actions"),
                    sec.getStringList("shift-actions"), sec.getStringList("shift-left-actions"), sec.getStringList("shift-right-actions"),
                    sec.contains("amount") ? sec.getInt("amount") : null,
                    sec.contains("custom-model-data") ? sec.getInt("custom-model-data") : null,
                    sec.contains("damage") ? sec.getInt("damage") : null,
                    Map.of(), List.of(), null,
                    sec.contains("hide-tooltip") ? sec.getBoolean("hide-tooltip") : null,
                    sec.getString("tooltip-style"),
                    sec.getDouble("cooldown", 0.0), sec.getString("cooldown-message"),
                    priorities
            );
        }

        public GuiItem applyOverride(ConfigurationSection sec) {
            if (sec == null) return this;
            return new GuiItem(
                    this.key, this.slots,
                    sec.getString("material", this.material),
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
                    this.enchants, this.flags, this.headOwner,
                    sec.get("hide-tooltip") instanceof Boolean b ? b : this.hideTooltip,
                    sec.getString("tooltip-style", this.tooltipStyle),
                    sec.get("cooldown") instanceof Number n ? n.doubleValue() : this.cooldown,
                    sec.getString("cooldown-message", this.cooldownMessage),
                    this.priorities
            );
        }

        public ItemStack buildStack(Player viewer, Map<String, String> placeholders, AirCore plugin) {
            ItemPriority match = null;
            for (ItemPriority p : priorities.values()) {
                if (p.matches(viewer, placeholders)) {
                    match = p;
                    break;
                }
            }

            String rawMat = (match != null && match.material() != null) ? match.material() : this.material;
            String appliedMat = PlaceholderUtil.apply(viewer, rawMat, placeholders);

            Material activeMat = Material.STONE;
            String activeHead = null;

            if (appliedMat.startsWith("head-")) {
                activeMat = Material.PLAYER_HEAD;
                activeHead = appliedMat.substring(5);
            } else {
                Material m = Material.matchMaterial(appliedMat.toUpperCase());
                if (m != null) activeMat = m;
            }

            ItemComponent builder = new ItemComponent(activeMat);

            String activeName = (match != null && match.displayName() != null) ? match.displayName() : this.rawName;
            if (activeName != null) {
                builder.name(MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, activeName, placeholders)));
            }

            List<String> activeLore = (match != null && match.lore() != null) ? match.lore() : this.rawLore;
            if (activeLore != null && !activeLore.isEmpty()) {
                List<Component> processedLore = new ArrayList<>();
                for (String line : activeLore) {
                    String applied = PlaceholderUtil.apply(viewer, line, placeholders);
                    for (String split : applied.split("\n")) {
                        processedLore.add(MM.deserialize("<!italic>" + split));
                    }
                }
                builder.lore(processedLore);
            }

            builder.amount((match != null && match.amount() != null) ? match.amount() : (this.amount != null ? this.amount : 1))
                    .customModelData((match != null && match.customModelData() != null) ? match.customModelData() : this.customModelData)
                    .damage(this.damage)
                    .enchants(this.enchants)
                    .glow((match != null && match.glow() != null) ? match.glow() : this.glow)
                    .flags(this.flags.toArray(new ItemFlag[0]))
                    .hideTooltip((match != null && match.hideTooltip() != null) ? match.hideTooltip() : (this.hideTooltip != null && this.hideTooltip))
                    .tooltipStyle((match != null && match.tooltipStyle() != null) ? match.tooltipStyle() : this.tooltipStyle)
                    .itemModel((match != null && match.itemModel() != null) ? match.itemModel() : this.itemModel);

            if (activeHead != null) {
                UUID ownerUuid = plugin.database().records().uuidFromName(activeHead);
                PlayerRecords.SkinData skin = (ownerUuid != null) ? plugin.database().records().getSkinData(ownerUuid) : null;
                builder.skullOwner(activeHead, viewer, skin);
            }

            return builder.build();
        }
    }
}