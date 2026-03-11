package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.module.economy.service.ItemWorthService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

public final class SellSlotMapper {

    private SellSlotMapper() {}

    public static void fillCustom(AirCore plugin, Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders) {
        var worthService = plugin.economy().worth();
        var formatService = plugin.economy().formats();
        boolean alwaysShow = def.config().getBoolean("always-show-buttons", true);
        Map<String, String> mutablePh = new HashMap<>(placeholders);

        for (GuiItem item : def.items().values()) {
            if (isReservedKey(item.key())) continue;
            renderItem(plugin, inv, item, viewer, mutablePh);
        }

        GuiItem confirm = def.items().get("confirm");
        double worth = calculateWorth(inv, worthService, def).total();
        mutablePh.put("worth", formatService.formatAmount(worth));
        mutablePh.put("worth-raw", String.valueOf(worth));

        if (confirm != null && (alwaysShow || worth > 0)) {
            renderItem(plugin, inv, confirm, viewer, mutablePh);
        }

        GuiItem confirmAll = def.items().get("confirm-all");
        double worthAll = calculateWorthAll(inv, worthService, def, viewer).total();
        mutablePh.put("worth-all", formatService.formatAmount(worthAll));
        mutablePh.put("worth-all-raw", String.valueOf(worthAll));

        if (confirmAll != null && (alwaysShow || worthAll > 0)) {
            renderItem(plugin, inv, confirmAll, viewer, mutablePh);
        }

        renderSpecificButton(plugin, inv, def, "cancel", viewer, mutablePh);
    }

    public static void fillConfirm(AirCore plugin, Inventory inv, GuiDefinition def, Player viewer, Map<String, String> placeholders) {
        Map<String, String> ph = new HashMap<>(placeholders);
        for (GuiItem item : def.items().values()) {
            if (item.key().equals("confirm") || item.key().equals("cancel")) continue;
            renderItem(plugin, inv, item, viewer, ph);
        }
        renderSpecificButton(plugin, inv, def, "confirm", viewer, ph);
        renderSpecificButton(plugin, inv, def, "cancel", viewer, ph);
    }

    private static void renderItem(AirCore plugin, Inventory inv, GuiItem item, Player p, Map<String, String> ph) {
        ItemStack stack = item.buildStack(p, ph, plugin);
        for (int slot : item.slots()) {
            if (slot < inv.getSize()) inv.setItem(slot, stack);
        }
    }

    private static void renderSpecificButton(AirCore plugin, Inventory inv, GuiDefinition def, String key, Player p, Map<String, String> ph) {
        GuiItem item = def.items().get(key);
        if (item != null) renderItem(plugin, inv, item, p, ph);
    }

    public static WorthResult calculateWorth(Inventory inv, ItemWorthService worthService, GuiDefinition def) {
        GuiItem sellGroup = def.items().get("sell-slots");
        if (sellGroup == null) return new WorthResult(0, false);

        double total = 0;
        boolean unsupported = false;

        for (int slot : sellGroup.slots()) {
            if (slot >= inv.getSize()) continue;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir() || isCustomFillerAt(def, slot, item)) continue;

            double price = worthService.getWorth(item);
            if (price <= 0) {
                unsupported = true;
            } else {
                total += price * item.getAmount();
            }
        }
        return new WorthResult(total, unsupported);
    }

    public static WorthResult calculateWorthAll(Inventory inv, ItemWorthService worthService, GuiDefinition def, Player viewer) {
        WorthResult guiResult = calculateWorth(inv, worthService, def);
        double total = guiResult.total();
        boolean unsupported = guiResult.hasUnsupported();

        for (ItemStack item : viewer.getInventory().getStorageContents()) {
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

    public static boolean isCustomFillerAt(GuiDefinition def, int slot, @Nullable ItemStack current) {
        if (current == null || current.getType().isAir()) return false;
        return findCustomItemAt(def, slot) != null || isReservedButtonSlot(def, slot);
    }

    private static boolean isReservedButtonSlot(GuiDefinition def, int slot) {
        return isSlotIn(def, "confirm", slot) || isSlotIn(def, "confirm-all", slot) || isSlotIn(def, "cancel", slot);
    }

    private static boolean isSlotIn(GuiDefinition def, String key, int slot) {
        GuiItem item = def.items().get(key);
        return item != null && item.slots().contains(slot);
    }

    public static GuiItem findCustomItemAt(GuiDefinition def, int slot) {
        for (GuiItem item : def.items().values()) {
            if (isReservedKey(item.key())) continue;
            if (item.slots().contains(slot)) return item;
        }
        return null;
    }

    private static boolean isReservedKey(String key) {
        return key.equalsIgnoreCase("sell-slots") ||
                key.equalsIgnoreCase("confirm") ||
                key.equalsIgnoreCase("confirm-all") ||
                key.equalsIgnoreCase("cancel");
    }

    public record WorthResult(double total, boolean hasUnsupported) {}
}