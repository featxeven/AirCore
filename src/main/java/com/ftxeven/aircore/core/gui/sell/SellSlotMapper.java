package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.economy.service.ItemWorthService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

public final class SellSlotMapper {

    private SellSlotMapper() {}

    public static void fillCustom(AirCore plugin,
                                  Inventory inv,
                                  GuiDefinition def,
                                  Player viewer,
                                  Map<String, String> placeholders) {

        var worthService = plugin.economy().worth();
        var formatService = plugin.economy().formats();
        boolean alwaysShow = def.config().getBoolean("always-show-buttons", true);

        Map<String, String> mutablePh = new HashMap<>(placeholders);

        GuiDefinition.GuiItem confirm = def.items().get("confirm");
        if (confirm != null) {
            double totalGui = calculateWorth(inv, worthService, def).total();
            if (alwaysShow || totalGui > 0) {
                mutablePh.put("worth", formatService.formatAmount(totalGui));
                mutablePh.put("worth-all", formatService.formatAmount(calculateWorthAll(inv, worthService, def, viewer).total()));

                ItemStack button = confirm.buildStack(viewer, mutablePh);
                for (int slot : confirm.slots()) {
                    if (slot < inv.getSize()) inv.setItem(slot, button);
                }
            } else {
                for (int slot : confirm.slots()) inv.setItem(slot, null);
            }
        }

        GuiDefinition.GuiItem confirmAll = def.items().get("confirm-all");
        if (confirmAll != null) {
            double totalAll = calculateWorthAll(inv, worthService, def, viewer).total();
            if (alwaysShow || totalAll > 0) {
                mutablePh.put("worth-all", formatService.formatAmount(totalAll));

                ItemStack button = confirmAll.buildStack(viewer, mutablePh);
                for (int slot : confirmAll.slots()) {
                    if (slot < inv.getSize()) inv.setItem(slot, button);
                }
            } else {
                for (int slot : confirmAll.slots()) inv.setItem(slot, null);
            }
        }

        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            if (isReservedKey(entry.getKey())) continue;

            GuiDefinition.GuiItem item = entry.getValue();
            ItemStack stack = item.buildStack(viewer, mutablePh);

            for (int slot : item.slots()) {
                if (slot < inv.getSize()) {
                    ItemStack existing = inv.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        inv.setItem(slot, stack);
                    }
                }
            }
        }
    }

    public static void fillConfirm(Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders) {
        Map<String, String> mutablePh = new HashMap<>(placeholders);
        for (GuiDefinition.GuiItem item : def.items().values()) {
            ItemStack stack = item.buildStack(viewer, mutablePh);
            for (int slot : item.slots()) {
                if (slot < inv.getSize()) inv.setItem(slot, stack);
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
            if (price <= 0) { unsupported = true; continue; }
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
            if (price > 0) total += price * item.getAmount();
            else unsupported = true;
        }
        return new WorthResult(total, unsupported);
    }

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, @Nullable ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        for (Map.Entry<String, GuiDefinition.GuiItem> entry : def.items().entrySet()) {
            if (isReservedKey(entry.getKey())) continue;
            if (entry.getValue().slots().contains(slot) && current.getType() == entry.getValue().material()) return true;
        }
        return false;
    }

    public static GuiDefinition.GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (Map.Entry<String, GuiDefinition.GuiItem> e : def.items().entrySet()) {
            if (isReservedKey(e.getKey())) continue;
            if (e.getValue().slots().contains(slot)) return e.getValue();
        }
        return null;
    }

    private static boolean isReservedKey(String key) {
        return "sell-slots".equalsIgnoreCase(key) || "confirm".equalsIgnoreCase(key) || "confirm-all".equalsIgnoreCase(key);
    }

    public record WorthResult(double total, boolean hasUnsupported) {}
}