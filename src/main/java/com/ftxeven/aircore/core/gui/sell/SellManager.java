package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.module.economy.EconomyManager;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
    private Set<Integer> cachedSellSlots = Collections.emptySet();

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

        Map<String, GuiItem> items = new LinkedHashMap<>();
        List<Integer> sellList = GuiDefinition.parseSlots(cfg.getStringList("sell-slots"));
        this.cachedSellSlots = new HashSet<>(sellList);
        items.put("sell-slots", createEmptyGroup(sellList));

        ConfigurationSection buttonsSec = cfg.getConfigurationSection("buttons");
        if (buttonsSec != null) {
            for (String key : buttonsSec.getKeys(false)) {
                ConfigurationSection sub = buttonsSec.getConfigurationSection(key);
                if (sub != null) items.put(key, GuiItem.fromSection(key, sub));
            }
        }

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                if (items.containsKey(key)) continue;
                ConfigurationSection sub = itemsSec.getConfigurationSection(key);
                if (sub != null) items.put(key, GuiItem.fromSection(key, sub));
            }
        }

        this.definition = new GuiDefinition(cfg.getString("title", "Sell"), cfg.getInt("rows", 5), items, cfg);
        confirmManager.loadDefinition();
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        Map<String, String> ph = new HashMap<>(placeholders);
        ph.put("player", viewer.getName());
        ph.put("worth", "0");
        ph.put("worth-all", "0");

        String title = PlaceholderUtil.apply(viewer, definition.title(), ph);
        Inventory inv = Bukkit.createInventory(new SellHolder(), definition.rows() * 9, mm.deserialize(title));

        if (inv.getHolder() instanceof SellHolder holder) holder.setInventory(inv);

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, ph);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;

        int slot = event.getSlot();

        if (clicked.equals(event.getView().getBottomInventory())) {
            ItemStack current = event.getCurrentItem();
            if (event.isShiftClick() && current != null && !current.getType().isAir()) {
                event.setCancelled(true);
                distributeToSlots(top, current, clicked, slot);
                refreshConfirmButton(top, viewer);
            }
            return;
        }

        event.setCancelled(true);

        for (String key : List.of("confirm", "confirm-all")) {
            GuiItem button = definition.items().get(key);
            if (button != null && button.slots().contains(slot)) {
                if (isButtonActive(key, top, viewer)) {
                    handleButtonPress(viewer, top, button, event.getClick());
                    return;
                }
            }
        }

        if (cachedSellSlots.contains(slot)) {
            event.setCancelled(false);
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> refreshConfirmButton(top, viewer), 1L);
            return;
        }

        for (GuiItem item : definition.items().values()) {
            if (item.slots().contains(slot)) {
                if (item.key().equals("confirm") || item.key().equals("confirm-all")) continue;

                handleAction(item, viewer, event.getClick(), Collections.emptyMap(), top);
                return;
            }
        }
    }

    private boolean isButtonActive(String key, Inventory inv, Player viewer) {
        if (definition.config().getBoolean("always-show-buttons", false)) return true;

        var worthSvc = plugin.economy().worth();
        if (key.equals("confirm-all")) {
            return SellSlotMapper.calculateWorthAll(inv, worthSvc, definition, viewer).total() > 0;
        }
        return SellSlotMapper.calculateWorth(inv, worthSvc, definition).total() > 0;
    }

    private void handleButtonPress(Player viewer, Inventory top, GuiItem button, ClickType click) {
        if (isOnCooldown(viewer, button)) return;

        boolean isAll = button.key().equals("confirm-all");
        var worthSvc = plugin.economy().worth();

        SellSlotMapper.WorthResult result = isAll ?
                SellSlotMapper.calculateWorthAll(top, worthSvc, definition, viewer) :
                SellSlotMapper.calculateWorth(top, worthSvc, definition);

        if (result.total() <= 0 && !definition.config().getBoolean("always-show-buttons", false)) return;

        String actionType = isAll ? "sell-all" : "sell";
        String configPath = "buttons." + button.key() + ".apply-confirm." + actionType + ".enabled";
        boolean confirmEnabled = definition.config().getBoolean(configPath, false);

        Map<String, String> ph = new HashMap<>();
        String fmt = plugin.economy().formats().formatAmount(result.total());
        ph.put("worth", fmt);
        ph.put("worth-all", fmt);
        ph.put("amount", fmt);
        ph.put("worth-raw", String.valueOf(result.total()));
        ph.put("player", viewer.getName());

        if (confirmEnabled && result.total() > 0) {
            List<String> rawActions = button.getActionsForClick(viewer, ph, click);
            if (rawActions != null && !rawActions.isEmpty()) {
                List<String> aestheticActions = rawActions.stream()
                        .filter(a -> !a.equalsIgnoreCase("[sell]") && !a.equalsIgnoreCase("[sell-all]"))
                        .toList();
                itemAction.executeAll(aestheticActions, viewer, ph);
            }

            confirmManager.open(viewer, top, result, isAll);
        } else {
            handleAction(button, viewer, click, ph, top);
        }
    }

    public void processSale(Player viewer, Inventory top, SellSlotMapper.WorthResult result, boolean isAll) {
        if (result.hasUnsupported() || result.total() <= 0) {
            MessageUtil.send(viewer, result.hasUnsupported() ? "economy.sell.error-failed" : "economy.sell.error-invalid", Map.of());
            return;
        }

        double amt = plugin.economy().formats().round(result.total());
        double max = plugin.config().economyMaxBalance();

        if (max > 0 && (plugin.economy().balances().getBalance(viewer.getUniqueId()) + amt) > max) {
            MessageUtil.send(viewer, "economy.sell.error-max-balance", Map.of("amount", plugin.economy().formats().formatAmount(max)));
            return;
        }

        if (plugin.economy().transactions().deposit(viewer.getUniqueId(), amt).type() == EconomyManager.ResultType.SUCCESS) {
            markSaleProcessed(viewer.getUniqueId());
            MessageUtil.send(viewer, "economy.sell.success", Map.of("amount", plugin.economy().formats().formatAmount(amt)));

            clearSellSlots(top);
            if (isAll) viewer.getInventory().clear();

            if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmManager.ConfirmHolder)) {
                refreshConfirmButton(top, viewer);
                viewer.updateInventory();
            }
        }
    }

    public void handleAction(GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh, Inventory inv) {
        if (item == null) return;
        List<String> actions = item.getActionsForClick(viewer, extraPh, click);
        if (actions == null || actions.isEmpty()) return;

        for (String action : actions) {
            if (action.equalsIgnoreCase("[sell]")) {
                processSale(viewer, inv, SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), definition), false);
            } else if (action.equalsIgnoreCase("[sell-all]")) {
                processSale(viewer, inv, SellSlotMapper.calculateWorthAll(inv, plugin.economy().worth(), definition, viewer), true);
            } else {
                itemAction.execute(action, viewer, extraPh);
            }
        }
    }

    public void refreshConfirmButton(Inventory inv, Player viewer) {
        if (!(inv.getHolder() instanceof SellHolder holder)) return;

        var worth = plugin.economy().worth();
        double guiW = SellSlotMapper.calculateWorth(inv, worth, definition).total();
        double allW = SellSlotMapper.calculateWorthAll(inv, worth, definition, viewer).total();

        if (holder.lastWorth == (guiW + allW)) return;
        holder.lastWorth = guiW + allW;

        Map<String, String> context = Map.of(
                "player", viewer.getName(),
                "worth", plugin.economy().formats().formatAmount(guiW),
                "worth-raw", String.valueOf(guiW),
                "worth-all", plugin.economy().formats().formatAmount(allW),
                "worth-all-raw", String.valueOf(allW)
        );

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, context);
    }

    private void distributeToSlots(Inventory top, ItemStack moving, Inventory src, int srcSlot) {
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
                if (top.getItem(dest) == null) {
                    top.setItem(dest, moving.clone());
                    moving.setAmount(0);
                    break;
                }
            }
        }
        if (moving.getAmount() <= 0) src.setItem(srcSlot, null);
    }

    private boolean isOnCooldown(Player p, GuiItem item) {
        if (plugin.gui().cooldowns().isOnCooldown(p, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(p, item);
            return true;
        }
        plugin.gui().cooldowns().applyCooldown(p, item);
        return false;
    }

    private GuiItem createEmptyGroup(List<Integer> slots) {
        return new GuiItem("sell-slots", slots, "AIR", null, List.of(), false, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, Map.of(), List.of(), null, null, null, 0.0, null, new TreeMap<>());
    }

    public void returnItems(Player player, Inventory inv) {
        GuiItem sellSlotsItem = definition.items().get("sell-slots");
        if (sellSlotsItem != null) listener.returnItemsToPlayer(player, inv, sellSlotsItem.slots());
    }

    public void markSaleProcessed(UUID p) { processedSales.add(p); plugin.scheduler().runDelayed(() -> processedSales.remove(p), 20L); }
    public boolean consumeProcessedSale(UUID p) { return processedSales.remove(p); }
    public void markTransitioning(UUID p) { transitioning.add(p); plugin.scheduler().runDelayed(() -> transitioning.remove(p), 2L); }
    public boolean isTransitioning(UUID p) { return transitioning.contains(p); }
    public ItemAction getActionProcessor() { return itemAction; }

    @Override public void cleanup() { HandlerList.unregisterAll(this.listener); confirmManager.cleanup(); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof SellHolder || inv.getHolder() instanceof ConfirmManager.ConfirmHolder; }
    @Override public void refresh(Inventory inv, Player v, Map<String, String> ph) { refreshConfirmButton(inv, v); }
    public GuiDefinition definition() { return definition; }
    private void clearSellSlots(Inventory top) { for (int slot : cachedSellSlots) if (slot < top.getSize()) top.setItem(slot, null); }

    public static class SellHolder implements InventoryHolder {
        public double lastWorth = -1.0;
        private Inventory inventory;
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}