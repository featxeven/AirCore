package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.service.FormatService;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.ItemComponent;
import com.ftxeven.aircore.core.economy.service.ItemWorthService;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

public final class SellSlotMapper {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SellSlotMapper() {}

    public static void fillCustom(AirCore plugin,
                                  Inventory inv,
                                  GuiDefinition def,
                                  Player viewer,
                                  Map<String, String> placeholders) {

        var worthService = plugin.economy().worth();
        var formatService = plugin.economy().formats();
        boolean alwaysShow = def.config().getBoolean("always-show-buttons", true);

        GuiDefinition.GuiItem confirm = def.items().get("confirm");
        if (confirm != null) {
            double totalGui = calculateWorth(inv, worthService, def).total();
            if (alwaysShow || totalGui > 0) {
                ItemStack button = createBaseItem(confirm, processHeadOwner(confirm.headOwner(), viewer, placeholders));
                for (int slot : confirm.slots()) {
                    if (slot < inv.getSize()) inv.setItem(slot, button.clone());
                }
                updateConfirmButton(def, confirm, inv, viewer, totalGui, calculateWorthAll(inv, worthService, def, viewer).total(), placeholders, formatService);
            } else {
                for (int slot : confirm.slots()) inv.setItem(slot, null);
            }
        }

        GuiDefinition.GuiItem confirmAll = def.items().get("confirm-all");
        if (confirmAll != null) {
            double totalAll = calculateWorthAll(inv, worthService, def, viewer).total();
            if (alwaysShow || totalAll > 0) {
                ItemStack button = createBaseItem(confirmAll, processHeadOwner(confirmAll.headOwner(), viewer, placeholders));
                for (int slot : confirmAll.slots()) {
                    if (slot < inv.getSize()) inv.setItem(slot, button.clone());
                }
                updateConfirmButton(def, confirmAll, inv, viewer, totalAll, totalAll, placeholders, formatService);
            } else {
                for (int slot : confirmAll.slots()) inv.setItem(slot, null);
            }
        }

        String playerName = placeholders.getOrDefault("player", viewer.getName());
        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            if (isReservedKey(key)) continue;

            GuiDefinition.GuiItem item = entry.getValue();
            ItemStack stack = createBaseItem(item, processHeadOwner(item.headOwner(), viewer, placeholders));
            applyTemplateMeta(stack, "items." + key, def, viewer, playerName);

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

    public static void fillConfirm(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders) {
        String total = placeholders.get("total");

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            GuiDefinition.GuiItem item = entry.getValue();
            ItemStack stack = createBaseItem(item, processHeadOwner(item.headOwner(), viewer, placeholders));

            String configPath = def.config().contains("buttons." + key) ? "buttons." + key : "items." + key;

            stack.editMeta(meta -> {
                String rawName = def.config().getString(configPath + ".display-name");
                if (rawName != null) {
                    String processedName = PlaceholderUtil.apply(viewer, rawName)
                            .replace("%total%", total != null ? total : "")
                            .replace("%player%", viewer.getName());
                    meta.displayName(MM.deserialize("<!italic>" + processedName));
                }

                List<String> rawLore = def.config().getStringList(configPath + ".lore");
                if (!rawLore.isEmpty()) {
                    meta.lore(rawLore.stream()
                            .map(line -> {
                                String processedLine = PlaceholderUtil.apply(viewer, line)
                                        .replace("%total%", total != null ? total : "")
                                        .replace("%player%", viewer.getName());
                                return MM.deserialize("<!italic>" + processedLine);
                            })
                            .toList());
                }
            });

            for (int slot : item.slots()) {
                if (slot < inv.getSize()) inv.setItem(slot, stack.clone());
            }
        }
    }

    private static void applyTemplateMeta(ItemStack stack, String configPath, GuiDefinition def, Player viewer, String playerName) {
        stack.editMeta(meta -> {
            String rawName = def.config().getString(configPath + ".display-name");
            if (rawName != null) {
                String processed = PlaceholderUtil.apply(viewer, rawName.replace("%player%", playerName));
                meta.displayName(MM.deserialize("<!italic>" + processed));
            }

            List<String> rawLore = def.config().getStringList(configPath + ".lore");
            if (!rawLore.isEmpty()) {
                meta.lore(rawLore.stream()
                        .map(line -> MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, line.replace("%player%", playerName))))
                        .toList());
            }
        });
    }

    public static void updateConfirmButton(GuiDefinition def,
                                           GuiDefinition.GuiItem confirm,
                                           Inventory inv,
                                           Player viewer,
                                           double worthValue,
                                           double worthAllValue,
                                           Map<String, String> placeholders,
                                           FormatService formatService) {

        String worth = formatService.formatAmount(worthValue);
        String worthAll = formatService.formatAmount(worthAllValue);
        String playerName = placeholders.getOrDefault("player", viewer.getName());

        String path = "buttons." + confirm.key();
        String rawName = def.config().getString(path + ".display-name");

        Component name = (rawName == null) ? null : MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer,
                rawName.replace("%worth%", worth).replace("%worth-all%", worthAll).replace("%player%", playerName)));

        List<String> rawLore = def.config().getStringList(path + ".lore");
        List<Component> lore = rawLore.isEmpty() ? Collections.emptyList() : rawLore.stream()
                .map(line -> MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer,
                        line.replace("%worth%", worth).replace("%worth-all%", worthAll).replace("%player%", playerName))))
                .toList();

        for (int slot : confirm.slots()) {
            if (slot < inv.getSize()) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    item.editMeta(meta -> {
                        if (name != null) meta.displayName(name);
                        meta.lore(lore);
                    });
                }
            }
        }
    }

    public static WorthResult calculateWorth(Inventory inv, ItemWorthService worthService, GuiDefinition def) {
        double total = 0;
        boolean unsupported = false;

        GuiDefinition.GuiItem sellGroup = def.items().get("sell-slots");
        if (sellGroup == null) return new WorthResult(0, false);

        for (int slot : sellGroup.slots()) {
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

    public static WorthResult calculateWorthAll(Inventory inv, ItemWorthService worthService, GuiDefinition def, Player viewer) {
        WorthResult guiResult = calculateWorth(inv, worthService, def);
        double total = guiResult.total();
        boolean unsupported = guiResult.hasUnsupported();

        for (int i = 0; i < 36; i++) {
            ItemStack item = viewer.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;

            double price = worthService.getWorth(item);
            if (price > 0) {
                total += price * item.getAmount();
            } else {
                unsupported = true;
            }
        }
        return new WorthResult(total, unsupported);
    }

    private static ItemStack createBaseItem(GuiDefinition.GuiItem item, String headOwner) {
        return new ItemComponent(item.material())
                .amount(item.amount() != null ? item.amount() : 1)
                .glow(item.glow())
                .itemModel(item.itemModel())
                .customModelData(item.customModelData())
                .damage(item.damage())
                .enchants(item.enchants())
                .flags(item.flags() != null ? item.flags().toArray(new ItemFlag[0]) : new ItemFlag[0])
                .skullOwner(headOwner)
                .hideTooltip(item.hideTooltip())
                .tooltipStyle(item.tooltipStyle())
                .build();
    }

    private static String processHeadOwner(String headOwner, Player viewer, Map<String, String> placeholders) {
        if (headOwner == null || headOwner.isBlank()) return null;
        String result = headOwner.replace("%player%", viewer.getName());
        String targetName = placeholders.get("target");
        if (targetName != null) result = result.replace("%target%", targetName);
        return PlaceholderUtil.apply(viewer, result);
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, @Nullable ItemStack current) {
        if (current == null || current.getType().isAir()) return false;

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            String key = entry.getKey();
            if (isReservedKey(key)) continue;

            GuiDefinition.GuiItem gi = entry.getValue();
            if (gi.slots().contains(slot) && current.getType() == gi.material()) {
                return true;
            }
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            String key = e.getKey();
            if (key.equalsIgnoreCase("confirm") || key.equalsIgnoreCase("confirm-all") || key.equalsIgnoreCase("sell-slots")) {
                continue;
            }

            if (e.getValue().slots().contains(slot)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean isReservedKey(String key) {
        return "sell-slots".equalsIgnoreCase(key) || "confirm".equalsIgnoreCase(key) || "confirm-all".equalsIgnoreCase(key);
    }

    public record WorthResult(double total, boolean hasUnsupported) {}
}