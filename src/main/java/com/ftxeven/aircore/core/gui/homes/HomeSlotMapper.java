package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.core.gui.GuiDefinition;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HomeSlotMapper {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());

    private HomeSlotMapper() {}

    public static void fillHomeInventory(Inventory inv,
                                         GuiDefinition def,
                                         Player viewer,
                                         int page,
                                         int maxPages,
                                         List<Map.Entry<String, Location>> homes,
                                         int[] homeSlots,
                                         Map<String, String> ph,
                                         int homeLimit) {

        ph.putIfAbsent("page", String.valueOf(page));
        ph.putIfAbsent("maxpages", String.valueOf(maxPages));
        ph.putIfAbsent("player", viewer.getName());

        for (GuiDefinition.GuiItem item : def.items().values()) {
            if (isReservedKey(item.key())) continue;
            renderItem(inv, item, viewer, ph);
        }

        boolean alwaysShow = def.config().getBoolean("always-show-buttons", false);
        if (page > 1 || alwaysShow) renderItem(inv, def.items().get("previous-page"), viewer, ph);
        if (page < maxPages || alwaysShow) renderItem(inv, def.items().get("next-page"), viewer, ph);
        renderItem(inv, def.items().get("sort-by"), viewer, ph);

        renderHomeGrid(inv, def, viewer, homes, homeSlots, page, ph, homeLimit);
    }

    private static void renderHomeGrid(Inventory inv, GuiDefinition def, Player viewer, List<Map.Entry<String, Location>> homes, int[] homeSlots, int page, Map<String, String> basePh, int homeLimit) {
        if (!(inv.getHolder() instanceof HomeManager.HomeHolder holder)) return;

        int pageSize = homeSlots.length;
        int start = (page - 1) * pageSize;

        ConfigurationSection homeSec = def.config().getConfigurationSection("home-item");
        ConfigurationSection availableSec = def.config().getConfigurationSection("slot-available");

        GuiDefinition.GuiItem homeTemplate = homeSec != null ? GuiDefinition.GuiItem.fromSection("home-item", homeSec) : null;
        GuiDefinition.GuiItem availableTemplate = (availableSec != null && availableSec.getBoolean("enabled", true))
                ? GuiDefinition.GuiItem.fromSection("slot-available", availableSec) : null;

        for (int i = 0; i < pageSize; i++) {
            int slot = homeSlots[i];
            int globalIndex = start + i;
            Map<String, String> itemPh = new HashMap<>(basePh);

            if (globalIndex < homes.size()) {
                if (homeTemplate == null) continue;
                Map.Entry<String, Location> home = homes.get(globalIndex);
                Location loc = home.getValue();
                Instant instant = Instant.ofEpochSecond(holder.getTimestamps().getOrDefault(home.getKey(), 0L));

                itemPh.put("name", home.getKey());
                itemPh.put("time", TIME_FORMAT.format(instant));
                itemPh.put("date", DATE_FORMAT.format(instant));
                itemPh.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "-");
                itemPh.put("x", String.valueOf(loc.getBlockX()));
                itemPh.put("y", String.valueOf(loc.getBlockY()));
                itemPh.put("z", String.valueOf(loc.getBlockZ()));
                inv.setItem(slot, homeTemplate.buildStack(viewer, itemPh));
            }
            else if (availableTemplate != null && globalIndex < homeLimit) {
                inv.setItem(slot, availableTemplate.buildStack(viewer, itemPh));
            }
        }
    }

    private static void renderItem(Inventory inv, GuiDefinition.GuiItem item, Player viewer, Map<String, String> ph) {
        if (item == null) return;
        ItemStack stack = item.buildStack(viewer, ph);
        for (int slot : item.slots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
    }

    private static boolean isReservedKey(String key) {
        return key.equals("home-item")
                || key.equals("next-page")
                || key.equals("previous-page")
                || key.equals("sort-by")
                || key.equals("slot-available");
    }
}