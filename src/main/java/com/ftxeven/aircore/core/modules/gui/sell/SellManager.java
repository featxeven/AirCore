package com.ftxeven.aircore.core.modules.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.economy.EconomyManager;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.modules.gui.GuiManager;
import com.ftxeven.aircore.core.modules.gui.ItemAction;
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
    private final Set<UUID> processedSales = ConcurrentHashMap.newKeySet();
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();
    private final SellListener listener;
    private final ConfirmManager confirmManager;

    private GuiDefinition definition;
    private GuiItem cachedConfirmItem;
    private Set<Integer> cachedSellSlots = Collections.emptySet();
    private Set<Integer> cachedConfirmSlots = Collections.emptySet();
    private boolean enabled;

    public SellManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        this.confirmManager = new ConfirmManager(plugin, this);
        loadDefinition();
        this.listener = new SellListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(this.listener, plugin);
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/sell/sell.yml");
        if (!file.exists()) plugin.saveResource("guis/sell/sell.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.enabled = cfg.getBoolean("enabled", true);

        Map<String, GuiItem> items = new LinkedHashMap<>();
        List<Integer> sellList = GuiDefinition.parseSlots(cfg.getStringList("sell-slots"));
        this.cachedSellSlots = new HashSet<>(sellList);
        items.put("sell-slots", createEmptyGroup(sellList));

        loadButton(cfg, items, "confirm", true);
        loadButton(cfg, items, "confirm-all", false);

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                if (items.containsKey(key)) continue;
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec != null) items.put(key, GuiItem.fromSection(key, sec));
            }
        }

        this.definition = new GuiDefinition(cfg.getString("title", "Place items here"), cfg.getInt("rows", 5), items, cfg);
        confirmManager.loadDefinition();
    }

    private void loadButton(YamlConfiguration cfg, Map<String, GuiItem> items, String key, boolean isConfirm) {
        ConfigurationSection sec = cfg.getConfigurationSection("buttons." + key);
        if (sec == null) return;
        GuiItem item = GuiItem.fromSection(key, sec);
        items.put(key, item);
        if (isConfirm) {
            this.cachedConfirmItem = item;
            this.cachedConfirmSlots = new HashSet<>(item.slots());
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        Map<String, String> context = new HashMap<>(placeholders);
        context.put("player", viewer.getName());
        context.put("worth", "0");
        context.put("worth-raw", "0");
        context.put("worth-all", "0");
        context.put("worth-all-raw", "0");

        String title = PlaceholderUtil.apply(viewer, definition.title().replace("%player%", viewer.getName()), context);
        Inventory inv = Bukkit.createInventory(new SellHolder(), definition.rows() * 9, mm.deserialize(title));

        if (inv.getHolder() instanceof SellHolder holder) {
            holder.setInventory(inv);
        }

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, context);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;

        ItemStack current = event.getCurrentItem();
        int slot = event.getSlot();

        if (clicked.equals(event.getView().getBottomInventory())) {
            if (event.isShiftClick() && current != null && !current.getType().isAir()) {
                event.setCancelled(true);
                distributeToSlots(top, current, clicked, slot);
                refreshConfirmButton(top, viewer);
            }
            return;
        }

        event.setCancelled(true);
        boolean inConfirm = cachedConfirmSlots.contains(slot);
        boolean inConfirmAll = isSlotInItem(slot);

        if (inConfirm || inConfirmAll) {
            handleButtonPress(viewer, top, inConfirmAll);
            return;
        }

        if (cachedSellSlots.contains(slot)) {
            if (SellSlotMapper.isCustomFillerAt(definition, slot, current) && event.getCursor().getType().isAir()) {
                handleAction(findCustomItemAt(slot), viewer, event.getClick(), Collections.emptyMap());
            } else {
                event.setCancelled(false);
                plugin.scheduler().runEntityTaskDelayed(viewer, () -> refreshConfirmButton(top, viewer), 1L);
            }
            return;
        }

        handleAction(findCustomItemAt(slot), viewer, event.getClick(), Collections.emptyMap());
    }

    private void handleButtonPress(Player viewer, Inventory top, boolean isAll) {
        String key = isAll ? "confirm-all" : "confirm";
        GuiItem button = definition.items().get(key);

        if (button == null || plugin.gui().cooldowns().isOnCooldown(viewer, button)) {
            if (button != null) plugin.gui().cooldowns().sendCooldownMessage(viewer, button);
            return;
        }

        var worthService = plugin.economy().worth();
        SellSlotMapper.WorthResult result = isAll ?
                SellSlotMapper.calculateWorthAll(top, worthService, definition, viewer) :
                SellSlotMapper.calculateWorth(top, worthService, definition);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", plugin.economy().formats().formatAmount(result.total()));
        ph.put("worth", plugin.economy().formats().formatAmount(result.total()));
        ph.put("worth-raw", String.valueOf(result.total()));
        ph.put("player", viewer.getName());

        plugin.gui().cooldowns().applyCooldown(viewer, button);
        handleAction(button, viewer, ClickType.LEFT, ph);

        boolean alwaysShow = definition.config().getBoolean("always-show-buttons", true);
        if (!alwaysShow && result.total() <= 0) return;

        double maxBalance = plugin.config().economyMaxBalance();
        if (maxBalance > 0) {
            double currentBalance = plugin.economy().balances().getBalance(viewer.getUniqueId());
            if ((currentBalance + result.total()) > maxBalance) {
                MessageUtil.send(viewer, "economy.sell.error-max-balance", Map.of(
                        "amount", plugin.economy().formats().formatAmount(maxBalance)
                ));
                return;
            }
        }

        if (definition.config().getBoolean("buttons." + key + ".apply-confirm", false) && result.total() > 0 && !result.hasUnsupported()) {
            confirmManager.open(viewer, top, result, isAll);
        } else {
            processSale(viewer, top, result, isAll);
        }
    }

    public void processSale(Player viewer, Inventory top, SellSlotMapper.WorthResult result, boolean isAll) {
        if (result.hasUnsupported() || result.total() <= 0) {
            MessageUtil.send(viewer, result.hasUnsupported() ? "economy.sell.error-failed" : "economy.sell.error-invalid", Map.of());
            return;
        }

        double totalSale = result.total();
        double rounded = plugin.economy().formats().round(totalSale);

        if (plugin.economy().transactions().deposit(viewer.getUniqueId(), rounded).type() == EconomyManager.ResultType.SUCCESS) {
            markSaleProcessed(viewer.getUniqueId());

            MessageUtil.send(viewer, "economy.sell.success", Map.of("amount", plugin.economy().formats().formatAmount(rounded)));

            if (definition.config().getBoolean("sell-logs-on-console", false)) {
                plugin.getLogger().info(viewer.getName() + " sold items for $" + rounded);
            }

            clearSellSlots(top);
            if (isAll) {
                viewer.getInventory().clear();
            }

            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmManager.ConfirmHolder) {
                viewer.closeInventory();
            } else {
                refreshConfirmButton(top, viewer);
                viewer.updateInventory();
            }
        } else {
            MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
        }
    }

    public void refreshConfirmButton(Inventory inv, Player viewer) {
        if (!(inv.getHolder() instanceof SellHolder holder)) return;

        var worthService = plugin.economy().worth();
        double guiWorth = SellSlotMapper.calculateWorth(inv, worthService, definition).total();
        double totalAll = SellSlotMapper.calculateWorthAll(inv, worthService, definition, viewer).total();

        if (holder.lastWorth == (guiWorth + totalAll)) return;
        holder.lastWorth = guiWorth + totalAll;

        boolean alwaysShow = definition.config().getBoolean("always-show-buttons", true);
        if (!alwaysShow) {
            if (guiWorth <= 0 && cachedConfirmItem != null) cachedConfirmItem.slots().forEach(s -> inv.setItem(s, null));
            GuiItem all = definition.items().get("confirm-all");
            if (totalAll <= 0 && all != null) all.slots().forEach(s -> inv.setItem(s, null));
        }

        Map<String, String> context = new HashMap<>();
        context.put("player", viewer.getName());
        context.put("worth", plugin.economy().formats().formatAmount(guiWorth));
        context.put("worth-raw", String.valueOf(guiWorth));
        context.put("worth-all", plugin.economy().formats().formatAmount(totalAll));
        context.put("worth-all-raw", String.valueOf(totalAll));

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, context);
    }

    private void distributeToSlots(Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        for (int dest : cachedSellSlots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem != null && destItem.isSimilar(moving)) {
                int transfer = Math.min(moving.getAmount(), destItem.getMaxStackSize() - destItem.getAmount());
                destItem.setAmount(destItem.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
            }
            if (moving.getAmount() <= 0) break;
        }
        if (moving.getAmount() > 0) {
            for (int dest : cachedSellSlots) {
                ItemStack item = top.getItem(dest);
                if (item == null || item.getType().isAir()) {
                    top.setItem(dest, moving.clone());
                    moving.setAmount(0);
                    break;
                }
            }
        }
        if (moving.getAmount() <= 0) sourceInv.setItem(sourceSlot, null);
    }

    private GuiItem createEmptyGroup(List<Integer> slots) {
        return new GuiItem("sell-slots", slots, Material.AIR, null, Collections.emptyList(), false, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null, null, null, Collections.emptyMap(),
                Collections.emptyList(), null, null, null, 0.0, null);
    }

    private boolean isSlotInItem(int slot) {
        GuiItem item = definition.items().get("confirm-all");
        return item != null && item.slots().contains(slot);
    }

    public void handleAction(GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh) {
        if (item == null) return;
        if (plugin.gui().cooldowns().isOnCooldown(viewer, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(viewer, item);
            return;
        }
        plugin.gui().cooldowns().applyCooldown(viewer, item);
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> ph = new HashMap<>(extraPh);
            ph.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, ph);
        }
    }

    public void returnItems(Player player, Inventory inv) {
        GuiItem sellSlotsItem = definition.items().get("sell-slots");
        if (sellSlotsItem == null) return;

        List<Integer> sellSlots = sellSlotsItem.slots();
        listener.returnItemsToPlayer(player, inv, sellSlots);
    }

    private GuiItem findCustomItemAt(int slot) { return SellSlotMapper.findCustomItemAt(definition, slot); }
    private void clearSellSlots(Inventory top) { for (int slot : cachedSellSlots) if (slot < top.getSize()) top.setItem(slot, null); }

    public void markSaleProcessed(UUID player) { processedSales.add(player); plugin.scheduler().runDelayed(() -> processedSales.remove(player), 20L); }
    public boolean consumeProcessedSale(UUID player) { return processedSales.remove(player); }
    public void markTransitioning(UUID player) { transitioning.add(player); plugin.scheduler().runDelayed(() -> transitioning.remove(player), 2L); }
    public boolean isTransitioning(UUID player) { return transitioning.contains(player); }
    public boolean isEnabled() { return enabled; }

    @Override public void cleanup() { HandlerList.unregisterAll(this.listener); confirmManager.cleanup(); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof SellHolder || inv.getHolder() instanceof ConfirmManager.ConfirmHolder; }
    @Override public void refresh(Inventory inv, Player v, Map<String, String> ph) { refreshConfirmButton(inv, v); }
    public GuiDefinition definition() { return definition; }

    public static class SellHolder implements InventoryHolder {
        public double lastWorth = -1.0;
        private Inventory inventory;
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}