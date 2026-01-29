package com.ftxeven.aircore.module.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.economy.service.FormatService;
import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.ItemComponent;
import com.ftxeven.aircore.module.core.economy.service.ItemWorthService;
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

public final class SellSlotMapper {
    private SellSlotMapper() {}

    public static void fillCustom(AirCore plugin,
                                  Inventory inv,
                                  GuiDefinition def,
                                  Player viewer,
                                  Map<String,String> placeholders) {
        MiniMessage mm = MiniMessage.miniMessage();

        // Place confirm button
        GuiDefinition.GuiItem confirm = def.items().get("confirm");
        if (confirm != null) {
            ItemStack button = new ItemComponent(confirm.material())
                    .amount(confirm.amount() != null ? confirm.amount() : 1)
                    .glow(confirm.glow())
                    .itemModel(confirm.itemModel())
                    .customModelData(confirm.customModelData())
                    .damage(confirm.damage())
                    .enchants(confirm.enchants())
                    .flags(confirm.flags() != null ? confirm.flags().toArray(new ItemFlag[0]) : new ItemFlag[0])
                    .skullOwner(confirm.skullOwner())
                    .hideTooltip(confirm.hideTooltip())
                    .tooltipStyle(confirm.tooltipStyle())
                    .build();

            for (int slot : confirm.slots()) {
                if (slot < inv.getSize()) {
                    inv.setItem(slot, button.clone());
                }
            }

            WorthResult result = calculateWorth(inv, plugin.economy().worth(), def);
            double total = result.total();
            updateConfirmButton(confirm, inv, viewer, total, placeholders, plugin.economy().formats());
        }

        // Place confirm-all button
        GuiDefinition.GuiItem confirmAll = def.items().get("confirm-all");
        if (confirmAll != null) {
            ItemStack button = new ItemComponent(confirmAll.material())
                    .amount(confirmAll.amount() != null ? confirmAll.amount() : 1)
                    .glow(confirmAll.glow())
                    .itemModel(confirmAll.itemModel())
                    .customModelData(confirmAll.customModelData())
                    .damage(confirmAll.damage())
                    .enchants(confirmAll.enchants())
                    .flags(confirmAll.flags() != null ? confirmAll.flags().toArray(new ItemFlag[0]) : new ItemFlag[0])
                    .skullOwner(confirmAll.skullOwner())
                    .hideTooltip(confirmAll.hideTooltip())
                    .tooltipStyle(confirmAll.tooltipStyle())
                    .build();

            for (int slot : confirmAll.slots()) {
                if (slot < inv.getSize()) {
                    inv.setItem(slot, button.clone());
                }
            }

            // Update display name and lore with combined worth
            double totalAll = calculateWorthAll(inv, plugin.economy().worth(), def, viewer);
            updateConfirmButton(confirmAll, inv, viewer, totalAll, placeholders, plugin.economy().formats());
        }

        // Place custom items
        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            GuiDefinition.GuiItem item = entry.getValue();

            // Skip reserved groups
            if ("sell-slots".equalsIgnoreCase(key) || "confirm".equalsIgnoreCase(key) || "confirm-all".equalsIgnoreCase(key)) continue;

            ItemComponent builder = new ItemComponent(item.material())
                    .amount(item.amount() != null ? item.amount() : 1)
                    .glow(item.glow())
                    .itemModel(item.itemModel())
                    .customModelData(item.customModelData())
                    .damage(item.damage())
                    .enchants(item.enchants())
                    .flags(item.flags() != null ? item.flags().toArray(new ItemFlag[0]) : new ItemFlag[0])
                    .skullOwner(item.skullOwner())
                    .hideTooltip(item.hideTooltip())
                    .tooltipStyle(item.tooltipStyle());

            ItemStack stack = builder.build();

            if (item.displayName() != null) {
                String raw = mm.serialize(item.displayName());
                raw = PlaceholderUtil.apply(viewer, raw);
                raw = raw.replace("%player%", placeholders.getOrDefault("player", viewer.getName()));

                final String finalRaw = raw;
                stack.editMeta(meta -> meta.displayName(mm.deserialize(finalRaw)));
            }

            if (item.lore() != null && !item.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (Component comp : item.lore()) {
                    String raw = mm.serialize(comp);
                    raw = PlaceholderUtil.apply(viewer, raw);
                    raw = raw.replace("%player%", placeholders.getOrDefault("player", viewer.getName()));
                    lore.add(mm.deserialize(raw));
                }
                stack.editMeta(meta -> meta.lore(lore));
            }

            for (int slot : item.slots()) {
                if (slot < inv.getSize()) {
                    ItemStack existing = inv.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        inv.setItem(slot, stack.clone());
                    }
                }
            }
        }
    }

    public static void updateConfirmButton(GuiDefinition.GuiItem confirm,
                                           Inventory inv,
                                           Player viewer,
                                           double total,
                                           Map<String,String> placeholders,
                                           FormatService formatService) {
        MiniMessage mm = MiniMessage.miniMessage();

        String formattedWorth = formatService.formatAmount(total);

        Component name = null;
        if (confirm.displayName() != null) {
            String raw = mm.serialize(confirm.displayName());
            raw = PlaceholderUtil.apply(viewer, raw);
            raw = raw.replace("%worth%", formattedWorth)
                    .replace("%player%", placeholders.getOrDefault("player", viewer.getName()));
            name = mm.deserialize(raw);
        }

        // Replace placeholders in lore
        List<Component> lore = new ArrayList<>();
        if (confirm.lore() != null) {
            for (Component comp : confirm.lore()) {
                String raw = mm.serialize(comp);
                raw = PlaceholderUtil.apply(viewer, raw);
                raw = raw.replace("%worth%", formattedWorth)
                        .replace("%player%", placeholders.getOrDefault("player", viewer.getName()));
                lore.add(mm.deserialize(raw));
            }
        }

        for (int slot : confirm.slots()) {
            if (slot < inv.getSize()) {
                ItemStack existing = inv.getItem(slot);
                if (existing != null && !existing.getType().isAir()) {
                    updateItemMeta(existing, name, lore);
                }
            }
        }
    }

    private static void updateItemMeta(ItemStack item, Component displayName, List<Component> lore) {
        var meta = item.getItemMeta();
        if (meta == null) return;

        if (displayName != null) {
            meta.displayName(displayName);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }

        item.setItemMeta(meta);
    }

    public static WorthResult calculateWorth(Inventory inv,
                                             ItemWorthService worthService,
                                             GuiDefinition def) {
        double total = 0;
        boolean unsupported = false;

        GuiDefinition.GuiItem sellGroup = def.items().get("sell-slots");
        if (sellGroup == null) return new WorthResult(0, false);

        int[] slots = sellGroup.slots().stream().mapToInt(Integer::intValue).toArray();
        for (int slot : slots) {
            if (slot >= inv.getSize()) continue;

            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            double price = worthService.getWorth(item);

            if (price <= 0) {
                unsupported = true;
                continue;
            }

            total += price * item.getAmount();
        }
        return new WorthResult(total, unsupported);
    }

    public static double calculateWorthAll(Inventory inv,
                                           ItemWorthService worthService,
                                           GuiDefinition def,
                                           Player viewer) {
        double total = 0;

        // Sell GUI slots
        GuiDefinition.GuiItem sellGroup = def.items().get("sell-slots");
        if (sellGroup != null) {
            for (int slot : sellGroup.slots()) {
                if (slot >= inv.getSize()) continue;
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                double price = worthService.getWorth(item);
                if (price > 0) total += price * item.getAmount();
            }
        }

        // Player inventory slots
        for (int i = 0; i < 36; i++) {
            ItemStack item = viewer.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            double price = worthService.getWorth(item);
            if (price > 0) total += price * item.getAmount();
        }

        return total;
    }

    public static GuiDefinition.GuiItem findItem(GuiDefinition def, int slot) {
        for (GuiDefinition.GuiItem item : def.items().values()) {
            for (int s : item.slots()) {
                if (s == slot) return item;
            }
        }
        return null;
    }

    public static boolean isDynamicSlot(GuiDefinition def, int slot) {
        GuiDefinition.GuiItem sellGroup = def.items().get("sell-slots");
        if (sellGroup == null) return false;
        for (int s : sellGroup.slots()) {
            if (s == slot) return true;
        }
        return false;
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, ItemStack current) {
        if (current == null || current.getType().isAir()) return false;

        if (!isDynamicSlot(def, slot)) return false;

        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            GuiDefinition.GuiItem gi = e.getValue();

            if ("sell-slots".equals(key)) continue; // skip dynamic group

            boolean slotMatches = false;
            for (int s : gi.slots()) {
                if (s == slot) {
                    slotMatches = true;
                    break;
                }
            }
            if (!slotMatches) continue;

            // Check if material matches
            if (current.getType() == gi.material()) return true;
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            GuiDefinition.GuiItem gi = e.getValue();
            if ("sell-slots".equals(key)) continue; // skip dynamic group

            for (int s : gi.slots()) {
                if (s == slot) return gi;
            }
        }
        return null;
    }

    public record WorthResult(double total, boolean hasUnsupported) {
    }
}