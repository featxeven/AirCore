package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HomeSlotMapper {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private HomeSlotMapper() {}

    private static List<String> processLore(Player viewer, Inventory inv, List<String> rawLore) {
        if (rawLore == null) return null;

        boolean isTargetGui = inv.getHolder() instanceof HomeTargetManager.HomeTargetHolder;

        String permissionNode = isTargetGui ? "aircore.command.delhome.others" : "aircore.command.delhome";
        boolean hasDeletePerm = viewer.hasPermission(permissionNode);

        List<String> processed = new ArrayList<>();
        for (String line : rawLore) {
            if (line.startsWith("permission:")) {
                if (hasDeletePerm) {
                    processed.add(line.substring("permission:".length()).trim());
                }
                continue;
            }
            processed.add(line);
        }
        return processed;
    }

    public static void fillHomeInventory(AirCore plugin, Inventory inv, GuiDefinition def, Player viewer, int page, int maxPages, List<Map.Entry<String, Location>> homes, int[] homeSlots, Map<String, String> ph, int homeLimit, boolean isInitialOpen) {
        ph.putIfAbsent("page", String.valueOf(page));
        ph.putIfAbsent("pages", String.valueOf(maxPages));
        ph.putIfAbsent("player", viewer.getName());

        for (GuiItem item : def.items().values()) {
            if (isHomeGridKey(item.key()) || isButtonKey(item.key())) continue;

            if (isInitialOpen) {
                renderItem(plugin, inv, item, viewer, ph);
            } else {
                for (int slot : item.slots()) {
                    if (isManagedSlot(slot, homeSlots, def)) {
                        inv.setItem(slot, item.buildStack(viewer, ph, plugin));
                    }
                }
            }
        }

        for (GuiItem item : def.items().values()) {
            if (!isButtonKey(item.key())) continue;

            boolean showNext = page < maxPages || def.config().getBoolean("always-show-buttons", false);
            boolean showPrev = page > 1 || def.config().getBoolean("always-show-buttons", false);

            if (item.key().equals("next-page") && !showNext) continue;
            if (item.key().equals("previous-page") && !showPrev) continue;

            renderItem(plugin, inv, item, viewer, ph);
        }

        renderHomeGrid(plugin, inv, def, viewer, homes, homeSlots, page, ph, homeLimit);
    }

    private static void renderHomeGrid(AirCore plugin, Inventory inv, GuiDefinition def, Player viewer, List<Map.Entry<String, Location>> homes, int[] homeSlots, int page, Map<String, String> basePh, int homeLimit) {
        Map<String, Long> timestamps;

        if (inv.getHolder() instanceof HomeManager.HomeHolder holder) {
            timestamps = holder.getTimestamps();
        } else if (inv.getHolder() instanceof HomeTargetManager.HomeTargetHolder holder) {
            timestamps = holder.getTimestamps();
        } else {
            return;
        }

        int pageSize = homeSlots.length;
        int start = (page - 1) * pageSize;

        ConfigurationSection homeSec = def.config().getConfigurationSection("home-item");
        if (homeSec == null) return;

        GuiItem baseHomeItem = GuiItem.fromSection("home-item", homeSec);
        ConfigurationSection worldTypesSec = homeSec.getConfigurationSection("world-types");

        ConfigurationSection availableSec = def.config().getConfigurationSection("slot-available");
        GuiItem availableTemplate = (availableSec != null && availableSec.getBoolean("enabled", false))
                ? GuiItem.fromSection("slot-available", availableSec) : null;

        for (int i = 0; i < pageSize; i++) {
            int slot = homeSlots[i];
            int globalIndex = start + i;
            ItemStack stackToSet = null;

            if (globalIndex < homes.size()) {
                Map.Entry<String, Location> home = homes.get(globalIndex);
                Location loc = home.getValue();
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";

                GuiItem finalItem = baseHomeItem;
                if (worldTypesSec != null && worldTypesSec.contains(worldName)) {
                    finalItem = baseHomeItem.applyOverride(worldTypesSec.getConfigurationSection(worldName));
                }

                long ts = timestamps.getOrDefault(home.getKey(), 0L) * 1000L;
                Instant instant = Instant.ofEpochMilli(ts);

                Map<String, String> itemPh = new HashMap<>(basePh);
                itemPh.put("name", home.getKey());
                itemPh.put("time", TIME_FORMAT.format(instant));
                itemPh.put("date", TimeUtil.formatDate(plugin, ts));
                itemPh.put("world", worldName);
                itemPh.put("x", Integer.toString(loc.getBlockX()));
                itemPh.put("y", Integer.toString(loc.getBlockY()));
                itemPh.put("z", Integer.toString(loc.getBlockZ()));

                GuiItem loreFilteredItem = new GuiItem(
                        finalItem.key(), finalItem.slots(), finalItem.material(), finalItem.rawName(),
                        processLore(viewer, inv, finalItem.rawLore()),
                        finalItem.glow(), finalItem.itemModel(), finalItem.actions(), finalItem.leftActions(),
                        finalItem.rightActions(), finalItem.shiftActions(), finalItem.shiftLeftActions(),
                        finalItem.shiftRightActions(), finalItem.amount(), finalItem.customModelData(),
                        finalItem.damage(), finalItem.enchants(), finalItem.flags(), finalItem.headOwner(),
                        finalItem.hideTooltip(), finalItem.tooltipStyle(), finalItem.cooldown(), finalItem.cooldownMessage()
                );

                stackToSet = loreFilteredItem.buildStack(viewer, itemPh, plugin);
            } else if (availableTemplate != null && globalIndex < homeLimit) {
                stackToSet = availableTemplate.buildStack(viewer, basePh, plugin);
            }

            if (stackToSet != null) {
                inv.setItem(slot, stackToSet);
            }
        }
    }

    private static void renderItem(AirCore plugin, Inventory inv, GuiItem item, Player viewer, Map<String, String> ph) {
        if (item == null) return;

        GuiItem loreFilteredItem = new GuiItem(
                item.key(), item.slots(), item.material(), item.rawName(),
                processLore(viewer, inv, item.rawLore()),
                item.glow(), item.itemModel(), item.actions(), item.leftActions(),
                item.rightActions(), item.shiftActions(), item.shiftLeftActions(),
                item.shiftRightActions(), item.amount(), item.customModelData(),
                item.damage(), item.enchants(), item.flags(), item.headOwner(),
                item.hideTooltip(), item.tooltipStyle(), item.cooldown(), item.cooldownMessage()
        );

        ItemStack stack = loreFilteredItem.buildStack(viewer, ph, plugin);

        for (int slot : item.slots()) {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
    }

    private static boolean isManagedSlot(int slot, int[] homeSlots, GuiDefinition def) {
        for (int hs : homeSlots) if (slot == hs) return true;
        for (GuiItem item : def.items().values()) {
            if (isButtonKey(item.key()) && item.slots().contains(slot)) return true;
        }
        return false;
    }

    private static boolean isHomeGridKey(String key) {
        return key.equals("home-item") || key.equals("slot-available");
    }

    private static boolean isButtonKey(String key) {
        return key.equals("next-page") || key.equals("previous-page") ||
                key.equals("sort-by") || key.equals("filter-by");
    }
}