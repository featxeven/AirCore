package com.ftxeven.aircore.module.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.economy.EconomyManager;
import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.GuiManager;
import com.ftxeven.aircore.module.gui.ItemAction;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SellManager implements GuiManager.CustomGuiManager {

    private final AirCore plugin;
    private final ItemAction itemAction;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private boolean enabled;
    private GuiDefinition definition;
    private GuiDefinition.GuiItem cachedConfirmItem;
    private int[] cachedSellSlots;
    private int[] cachedConfirmSlots;
    private final SellListener listener;
    private final Set<UUID> processedSales = ConcurrentHashMap.newKeySet();

    public SellManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        loadDefinition();

        this.listener = new SellListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(this.listener, plugin);
    }

    public boolean isEnabled() { return enabled; }

    private void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/sell.yml");
        if (!file.exists()) plugin.saveResource("guis/sell.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.enabled = cfg.getBoolean("enabled", true);

        String title = cfg.getString("title", "Place your items here");
        int rows = cfg.getInt("rows", 5);

        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        List<Integer> sellSlotList = GuiDefinition.parseSlots(cfg.getStringList("sell-slots"));
        this.cachedSellSlots = sellSlotList.stream().mapToInt(Integer::intValue).toArray();

        items.put("sell-slots", new GuiDefinition.GuiItem(
                "sell-slots", sellSlotList, Material.AIR, null, Collections.emptyList(),
                false, null, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                null, null, null, Collections.emptyMap(), Collections.emptyList(),
                null, null, null
        ));

        ConfigurationSection confirmSec = cfg.getConfigurationSection("buttons.confirm");
        if (confirmSec != null) {
            GuiDefinition.GuiItem confirmItem = GuiDefinition.GuiItem.fromSection("confirm", confirmSec, mm);
            this.cachedConfirmItem = confirmItem;
            this.cachedConfirmSlots = confirmItem.slots().stream().mapToInt(Integer::intValue).toArray();
            items.put("confirm", confirmItem);
        }

        ConfigurationSection confirmAllSec = cfg.getConfigurationSection("buttons.confirm-all");
        if (confirmAllSec != null) {
            items.put("confirm-all", GuiDefinition.GuiItem.fromSection("confirm-all", confirmAllSec, mm));
        }

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                if ("confirm".equalsIgnoreCase(key) || "sell-slots".equalsIgnoreCase(key)) continue;
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                if (itemSec == null) continue;
                items.put(key, GuiDefinition.GuiItem.fromSection(key, itemSec, mm));
            }
        }
        this.definition = new GuiDefinition(title, rows, items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String rawTitle = PlaceholderUtil.apply(viewer, definition.title());
        rawTitle = rawTitle.replace("%player%", viewer.getName());

        Inventory inv = Bukkit.createInventory(new SellHolder(), definition.rows() * 9, mm.deserialize(rawTitle));

        Map<String, String> ph = new HashMap<>(placeholders);
        ph.putIfAbsent("player", viewer.getName());

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, ph);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();

        if (clicked == null || event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (event.isShiftClick() && clicked == bottom) {
            if (current == null || current.getType().isAir()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            distributeToSlots(top, current, clicked, event.getSlot());
            refreshConfirmButton(top, viewer);
            return;
        }

        if (clicked != top) return;

        boolean dynamic = isDynamicSlot(slot);
        boolean isFillerHere = isCustomFillerAt(slot, current);
        GuiDefinition.GuiItem registeredItem = findItem(slot);
        boolean registered = registeredItem != null;
        boolean inConfirm = isInConfirmSlots(slot);
        boolean inConfirmAll = definition.items().containsKey("confirm-all") &&
                definition.items().get("confirm-all").slots().contains(slot);

        if (!registered && !inConfirm && !inConfirmAll) {
            event.setCancelled(true);
            return;
        }

        if (inConfirm) {
            event.setCancelled(true);
            SellSlotMapper.WorthResult result = SellSlotMapper.calculateWorth(top, plugin.economy().worth(), definition);
            processSale(viewer, top, result, false, event.getClick());
            return;
        }

        if (inConfirmAll) {
            event.setCancelled(true);
            SellSlotMapper.WorthResult result = SellSlotMapper.calculateWorthAll(top, plugin.economy().worth(), definition, viewer);
            processSale(viewer, top, result, true, event.getClick());
            return;
        }

        if (dynamic) {
            if (isFillerHere && cursor.getType().isAir()) {
                event.setCancelled(true);
                GuiDefinition.GuiItem custom = findCustomItemAt(slot);
                if (custom != null) {
                    executeItemActions(custom, viewer, event.getClick(), Collections.emptyMap());
                }
                return;
            }

            if (isFillerHere && !cursor.getType().isAir()) {
                event.setCancelled(true);
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                refreshConfirmButton(top, viewer);
                return;
            }

            event.setCancelled(false);
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> refreshConfirmButton(top, viewer), 1L);
            return;
        }

        event.setCancelled(true);
        executeItemActions(registeredItem, viewer, event.getClick(), Collections.emptyMap());
    }

    private void processSale(Player viewer, Inventory top, SellSlotMapper.WorthResult result, boolean isAll, ClickType click) {
        if (result.hasUnsupported()) {
            MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
            viewer.updateInventory();
            return;
        }

        double total = result.total();
        if (total <= 0) {
            MessageUtil.send(viewer, "economy.sell.error-invalid", Map.of());
            viewer.updateInventory();
            return;
        }

        double rounded = plugin.economy().formats().round(total);
        String formatted = plugin.economy().formats().formatAmount(rounded);

        if (plugin.economy().transactions().deposit(viewer.getUniqueId(), rounded).type() == EconomyManager.ResultType.SUCCESS) {
            MessageUtil.send(viewer, "economy.sell.success", Map.of("amount", formatted));

            clearSellSlots(top);
            if (isAll) clearPlayerInv(viewer);

            markSaleProcessed(viewer.getUniqueId());
            refreshConfirmButton(top, viewer);
            viewer.updateInventory();

            GuiDefinition.GuiItem targetItem = isAll ? definition.items().get("confirm-all") : cachedConfirmItem;
            if (targetItem != null) {
                executeItemActions(targetItem, viewer, click, Map.of("amount", formatted));
            }
        } else {
            MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
        }
    }

    private void clearSellSlots(Inventory top) {
        for (int slot : cachedSellSlots) if (slot < top.getSize()) top.setItem(slot, null);
    }

    private void clearPlayerInv(Player viewer) {
        for (int i = 0; i < 36; i++) viewer.getInventory().setItem(i, null);
    }

    private void executeItemActions(GuiDefinition.GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh) {
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> ph = new HashMap<>(extraPh);
            ph.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, ph);
        }
    }

    public void refreshConfirmButton(Inventory inv, Player viewer) {
        if (cachedConfirmItem == null || !(inv.getHolder() instanceof SellHolder holder)) return;

        var worthService = plugin.economy().worth();
        double guiWorth = SellSlotMapper.calculateWorth(inv, worthService, definition).total();
        double totalAll = SellSlotMapper.calculateWorthAll(inv, worthService, definition, viewer).total();

        double currentState = guiWorth + totalAll;
        if (holder.lastWorth == currentState) return;
        holder.lastWorth = currentState;

        SellSlotMapper.updateConfirmButton(definition, cachedConfirmItem, inv, viewer, guiWorth, totalAll, Map.of("player", viewer.getName()), plugin.economy().formats());

        GuiDefinition.GuiItem confirmAll = definition.items().get("confirm-all");
        if (confirmAll != null) {
            SellSlotMapper.updateConfirmButton(definition, confirmAll, inv, viewer, totalAll, totalAll, Map.of("player", viewer.getName()), plugin.economy().formats());
        }
    }

    private void distributeToSlots(Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        if (moving == null || moving.getType().isAir()) return;

        for (int dest : cachedSellSlots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem != null && !destItem.getType().isAir() && destItem.isSimilar(moving)) {
                int transfer = Math.min(moving.getAmount(), destItem.getMaxStackSize() - destItem.getAmount());
                if (transfer > 0) {
                    destItem.setAmount(destItem.getAmount() + transfer);
                    moving.setAmount(moving.getAmount() - transfer);
                    if (moving.getAmount() <= 0) {
                        sourceInv.setItem(sourceSlot, null);
                        return;
                    }
                }
            }
        }

        for (int dest : cachedSellSlots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem == null || destItem.getType().isAir()) {
                top.setItem(dest, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    private boolean isDynamicSlot(int slot) { for (int s : cachedSellSlots) if (s == slot) return true; return false; }
    private boolean isInConfirmSlots(int slot) { if (cachedConfirmSlots == null) return false; for (int s : cachedConfirmSlots) if (s == slot) return true; return false; }
    private boolean isCustomFillerAt(int slot, @Nullable ItemStack current) { return SellSlotMapper.isCustomFillerAt(definition, slot, current); }
    private GuiDefinition.GuiItem findItem(int slot) { return SellSlotMapper.findItem(definition, slot); }
    private GuiDefinition.GuiItem findCustomItemAt(int slot) { return SellSlotMapper.findCustomItemAt(definition, slot); }

    public void executeConfirmActions(Player viewer, String amountFormatted) {
        if (cachedConfirmItem != null) executeItemActions(cachedConfirmItem, viewer, ClickType.LEFT, Map.of("amount", amountFormatted));
    }

    public void markSaleProcessed(UUID player) {
        processedSales.add(player);
        plugin.scheduler().runDelayed(() -> processedSales.remove(player), 20L);
    }

    public boolean consumeProcessedSale(UUID player) { return processedSales.remove(player); }
    public void unregisterListeners() { HandlerList.unregisterAll(this.listener); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof SellHolder; }
    public GuiDefinition definition() { return definition; }

    public static class SellHolder implements InventoryHolder {
        public double lastWorth = -1.0;
        private Inventory inventory;
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}