package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
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
    private final ConfirmManager confirmManager;
    private final Set<UUID> processedSales = ConcurrentHashMap.newKeySet();
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();

    public SellManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        loadDefinition();

        this.confirmManager = new ConfirmManager(plugin, this);
        this.listener = new SellListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(this.listener, plugin);
    }

    public boolean isEnabled() { return enabled; }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/sell/sell.yml");
        if (!file.exists()) plugin.saveResource("guis/sell/sell.yml", false);

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

        if (confirmManager != null) confirmManager.loadDefinition();
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

        boolean inConfirm = isInConfirmSlots(slot);
        boolean inConfirmAll = definition.items().containsKey("confirm-all") &&
                definition.items().get("confirm-all").slots().contains(slot);

        SellSlotMapper.WorthResult result = null;
        if (inConfirm) {
            result = SellSlotMapper.calculateWorth(top, plugin.economy().worth(), definition);
        } else if (inConfirmAll) {
            result = SellSlotMapper.calculateWorthAll(top, plugin.economy().worth(), definition, viewer);
        }

        boolean isAlwaysShow = definition.config().getBoolean("always-show-buttons", true);
        boolean isButtonActive = (inConfirm || inConfirmAll) && (isAlwaysShow || result.total() > 0);

        GuiDefinition.GuiItem customItem = findCustomItemAt(slot);
        if (customItem != null && current != null && current.getType() == customItem.material()) {

            if (customItem.key().equals("test") || !(inConfirm || inConfirmAll) || !isButtonActive) {
                event.setCancelled(true);
                executeItemActions(customItem, viewer, event.getClick(), Collections.emptyMap());
                return;
            }
        }

        if (isButtonActive && current != null && !current.getType().isAir()) {
            event.setCancelled(true);

            String key = inConfirm ? "confirm" : "confirm-all";
            GuiDefinition.GuiItem buttonItem = definition.items().get(key);

            if (buttonItem != null) {
                String formatted = plugin.economy().formats().formatAmount(result.total());
                executeItemActions(buttonItem, viewer, event.getClick(), Map.of("amount", formatted));
            }

            boolean applyConfirm = definition.config().getBoolean("buttons." + key + ".apply-confirm", false);

            if (applyConfirm && result.total() > 0 && !result.hasUnsupported()) {
                confirmManager.open(viewer, top, result, inConfirmAll);
            } else {
                processSale(viewer, top, result, inConfirmAll);
            }
            return;
        }

        boolean dynamic = isDynamicSlot(slot);
        if (dynamic) {
            if (SellSlotMapper.isCustomFillerAt(definition, slot, current) && cursor.getType().isAir()) {
                event.setCancelled(true);
                GuiDefinition.GuiItem filler = findCustomItemAt(slot);
                if (filler != null) executeItemActions(filler, viewer, event.getClick(), Collections.emptyMap());
                return;
            }

            event.setCancelled(false);
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> refreshConfirmButton(top, viewer), 1L);
            return;
        }

        event.setCancelled(true);
    }

    public void processSale(Player viewer, Inventory top, SellSlotMapper.WorthResult result, boolean isAll) {
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

            if (definition.config().getBoolean("sell-logs-on-console", false)) {
                plugin.getLogger().info(viewer.getName() + " sold items for $" + formatted);
            }

            clearSellSlots(top);
            if (isAll) clearPlayerInv(viewer);

            markSaleProcessed(viewer.getUniqueId());
            refreshConfirmButton(top, viewer);
            viewer.updateInventory();
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

    public void executeItemActions(GuiDefinition.GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh) {
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> ph = new HashMap<>(extraPh);
            ph.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, ph);
        }
    }

    public void refreshConfirmButton(Inventory inv, Player viewer) {
        if (!(inv.getHolder() instanceof SellHolder holder)) return;

        var worthService = plugin.economy().worth();
        double guiWorth = SellSlotMapper.calculateWorth(inv, worthService, definition).total();
        double totalAll = SellSlotMapper.calculateWorthAll(inv, worthService, definition, viewer).total();

        double currentState = guiWorth + totalAll;
        if (holder.lastWorth == currentState) return;
        holder.lastWorth = currentState;

        boolean alwaysShow = definition.config().getBoolean("always-show-buttons", true);
        Map<String, String> placeholders = Map.of("player", viewer.getName());

        if (cachedConfirmItem != null && !alwaysShow && guiWorth <= 0) {
            for (int slot : cachedConfirmItem.slots()) inv.setItem(slot, null);
        }

        GuiDefinition.GuiItem confirmAll = definition.items().get("confirm-all");
        if (confirmAll != null && !alwaysShow && totalAll <= 0) {
            for (int slot : confirmAll.slots()) inv.setItem(slot, null);
        }

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, placeholders);
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

    private GuiDefinition.GuiItem findCustomItemAt(int slot) { return SellSlotMapper.findCustomItemAt(definition, slot); }

    public void markSaleProcessed(UUID player) {
        processedSales.add(player);
        plugin.scheduler().runDelayed(() -> processedSales.remove(player), 20L);
    }

    public boolean consumeProcessedSale(UUID player) { return processedSales.remove(player); }

    public void markTransitioning(UUID player) {
        transitioning.add(player);
        plugin.scheduler().runDelayed(() -> transitioning.remove(player), 2L);
    }

    public boolean isTransitioning(UUID player) { return transitioning.contains(player); }

    public void returnItems(Player player, Inventory inv) {
        var sellSlots = definition.items().get("sell-slots").slots();
        listener.returnItemsToPlayer(player, inv, sellSlots);
    }

    public void unregisterListeners() {
        HandlerList.unregisterAll(this.listener);
        if (confirmManager != null) {
            HandlerList.unregisterAll(this.confirmManager);
        }
    }

    @Override
    public boolean owns(Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        return holder instanceof SellHolder || holder instanceof ConfirmManager.ConfirmHolder;
    }
    public GuiDefinition definition() { return definition; }

    public static class SellHolder implements InventoryHolder {
        public double lastWorth = -1.0;
        private Inventory inventory;
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}