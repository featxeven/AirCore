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
import java.util.Set;
import java.util.UUID;
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

        // Dynamic group
        List<Integer> sellSlotList = GuiDefinition.parseSlots(cfg.getStringList("sell-slots"));
        this.cachedSellSlots = sellSlotList.stream().mapToInt(Integer::intValue).toArray();

        items.put("sell-slots", new GuiDefinition.GuiItem(
                "sell-slots",
                sellSlotList,
                Material.AIR,
                null,
                Collections.emptyList(),
                false,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                null,
                Collections.emptyMap(),
                Collections.emptyList(),
                null,
                null,
                null
        ));

        // Confirm button
        ConfigurationSection confirmSec = cfg.getConfigurationSection("buttons.confirm");
        if (confirmSec != null) {
            GuiDefinition.GuiItem confirmItem = GuiDefinition.GuiItem.fromSection("confirm", confirmSec, mm);
            this.cachedConfirmItem = confirmItem;
            this.cachedConfirmSlots = confirmItem.slots().stream().mapToInt(Integer::intValue).toArray();
            items.put("confirm", confirmItem);
        }

        // Confirm-all button
        ConfigurationSection confirmAllSec = cfg.getConfigurationSection("buttons.confirm-all");
        if (confirmAllSec != null) {
            GuiDefinition.GuiItem confirmAllItem = GuiDefinition.GuiItem.fromSection("confirm-all", confirmAllSec, mm);
            items.put("confirm-all", confirmAllItem);
        }

        // Custom items
        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                if ("confirm".equalsIgnoreCase(key) || "sell-slots".equalsIgnoreCase(key)) continue;
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                if (itemSec == null) continue;

                GuiDefinition.GuiItem guiItem = GuiDefinition.GuiItem.fromSection(key, itemSec, mm);
                items.put(key, guiItem);
            }
        }

        this.definition = new GuiDefinition(title, rows, items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String rawTitle = PlaceholderUtil.apply(viewer, definition.title());
        rawTitle = rawTitle.replace("%player%", viewer.getName());

        Inventory inv = Bukkit.createInventory(new SellHolder(viewer.getUniqueId()), definition.rows() * 9, mm.deserialize(rawTitle));

        if (!placeholders.containsKey("player")) {
            placeholders = new HashMap<>(placeholders);
            placeholders.put("player", viewer.getName());
        }

        SellSlotMapper.fillCustom(plugin, inv, definition, viewer, placeholders);

        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clicked == null || event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            return;
        }

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

        // Confirm button behaviour
        if (inConfirm) {
            event.setCancelled(true);

            SellSlotMapper.WorthResult result =
                    SellSlotMapper.calculateWorth(top, plugin.economy().worth(), definition);
            double total = result.total();

            if (result.hasUnsupported()) {
                MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
                viewer.updateInventory();
                return;
            }

            if (total <= 0) {
                MessageUtil.send(viewer, "economy.sell.error-invalid", Map.of());
                viewer.updateInventory();
                return;
            }

            double rounded = plugin.economy().formats().round(total);
            String formatted = plugin.economy().formats().formatAmount(rounded);

            var econResult = plugin.economy().transactions().deposit(viewer.getUniqueId(), rounded);

            if (econResult.type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(viewer, "economy.sell.success", Map.of("amount", formatted));

                if (definition.getBoolean("sell-logs-on-console", true)) {
                    plugin.getLogger().info(viewer.getName() + " sold items for " + formatted);
                }

                // Clear slots first
                var sellSlots = definition.items().get("sell-slots").slots();
                for (int sellSlot : sellSlots) {
                    if (sellSlot < top.getSize()) {
                        top.setItem(sellSlot, null);
                    }
                }

                // Mark sale processed before actions
                markSaleProcessed(viewer.getUniqueId());

                refreshConfirmButton(top, viewer);
                viewer.updateInventory();

                if (cachedConfirmItem != null) {
                    List<String> actionsToExecute = cachedConfirmItem.getActionsForClick(event.getClick());
                    if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                        Map<String, String> ph = Map.of("player", viewer.getName(), "amount", formatted);
                        itemAction.executeAll(actionsToExecute, viewer, ph);
                    }
                }
            } else {
                MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
                viewer.updateInventory();
            }
            return;
        }

        // Confirm-all button behaviour
        if (inConfirmAll) {
            event.setCancelled(true);

            double total = 0;
            boolean unsupported = false;

            // Sell GUI slots
            for (int sellSlot : definition.items().get("sell-slots").slots()) {
                if (sellSlot >= top.getSize()) continue;
                ItemStack item = top.getItem(sellSlot);
                if (item == null || item.getType().isAir()) continue;
                double price = plugin.economy().worth().getWorth(item);
                if (price <= 0) { unsupported = true; continue; }
                total += price * item.getAmount();
            }

            // Player inventory slots
            for (int i = 0; i < 36; i++) {
                ItemStack item = viewer.getInventory().getItem(i);
                if (item == null || item.getType().isAir()) continue;
                double price = plugin.economy().worth().getWorth(item);
                if (price <= 0) { unsupported = true; continue; }
                total += price * item.getAmount();
            }

            if (unsupported) {
                MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
                return;
            }
            if (total <= 0) {
                MessageUtil.send(viewer, "economy.sell.error-invalid", Map.of());
                return;
            }

            double rounded = plugin.economy().formats().round(total);
            String formatted = plugin.economy().formats().formatAmount(rounded);

            var econResult = plugin.economy().transactions().deposit(viewer.getUniqueId(), rounded);
            if (econResult.type() == EconomyManager.ResultType.SUCCESS) {
                MessageUtil.send(viewer, "economy.sell.success", Map.of("amount", formatted));

                if (definition.getBoolean("sell-logs-on-console", true)) {
                    plugin.getLogger().info(viewer.getName() + " sold ALL items for " + formatted);
                }

                // Clear sell GUI slots
                for (int sellSlot : definition.items().get("sell-slots").slots()) {
                    if (sellSlot < top.getSize()) {
                        top.setItem(sellSlot, null);
                    }
                }

                // Clear player inventory
                for (int i = 0; i < 36; i++) {
                    viewer.getInventory().setItem(i, null);
                }

                // Mark sale processed before actions
                markSaleProcessed(viewer.getUniqueId());

                refreshConfirmButton(top, viewer);
                viewer.updateInventory();

                GuiDefinition.GuiItem confirmAllItem = definition.items().get("confirm-all");
                if (confirmAllItem != null) {
                    List<String> actionsToExecute = confirmAllItem.getActionsForClick(event.getClick());
                    if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                        Map<String, String> ph = Map.of("player", viewer.getName(), "amount", formatted);
                        itemAction.executeAll(actionsToExecute, viewer, ph);
                    }
                }
            } else {
                MessageUtil.send(viewer, "economy.sell.error-failed", Map.of());
            }
            return;
        }

        // Dynamic slot behaviour
        if (dynamic) {
            if (isFillerHere && cursor.getType().isAir()) {
                event.setCancelled(true);
                GuiDefinition.GuiItem custom = findCustomItemAt(slot);
                if (custom != null) {
                    List<String> actionsToExecute = custom.getActionsForClick(event.getClick());
                    if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                        itemAction.executeAll(actionsToExecute, viewer, Map.of("player", viewer.getName()));
                    }
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
            plugin.scheduler().runEntityTaskDelayed(viewer,
                    () -> refreshConfirmButton(top, viewer),
                    1L
            );
            return;
        }

        // Static/custom item slots
        event.setCancelled(true);

        List<String> actionsToExecute = registeredItem.getActionsForClick(event.getClick());
        if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
            itemAction.executeAll(actionsToExecute, viewer, Map.of("player", viewer.getName()));
        }
    }

    private void distributeToSlots(Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        if (moving == null || moving.getType().isAir()) return;

        // Merge into partial stacks first
        for (int dest : cachedSellSlots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem != null && destItem.isSimilar(moving) && destItem.getAmount() < destItem.getMaxStackSize()) {
                int transfer = Math.min(moving.getAmount(), destItem.getMaxStackSize() - destItem.getAmount());
                destItem.setAmount(destItem.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }

        // Then fill empty slots
        for (int dest : cachedSellSlots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem == null || destItem.getType().isAir()) {
                top.setItem(dest, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    private boolean isDynamicSlot(int slot) {
        for (int s : cachedSellSlots) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean isInConfirmSlots(int slot) {
        if (cachedConfirmSlots == null) return false;
        for (int s : cachedConfirmSlots) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean isCustomFillerAt(int slot, @Nullable ItemStack current) {
        return SellSlotMapper.isCustomFillerAt(definition, slot, current);
    }

    @Nullable
    private GuiDefinition.GuiItem findItem(int slot) {
        return SellSlotMapper.findItem(definition, slot);
    }

    @Nullable
    private GuiDefinition.GuiItem findCustomItemAt(int slot) {
        return SellSlotMapper.findCustomItemAt(definition, slot);
    }

    private void refreshConfirmButton(Inventory inv, Player viewer) {
        if (cachedConfirmItem == null) return;

        SellSlotMapper.WorthResult result =
                SellSlotMapper.calculateWorth(inv, plugin.economy().worth(), definition);

        double total = result.total();

        SellSlotMapper.updateConfirmButton(
                cachedConfirmItem,
                inv,
                viewer,
                total,
                Map.of("player", viewer.getName()),
                plugin.economy().formats()
        );
    }

    public void executeConfirmActions(Player viewer, String amountFormatted) {
        if (cachedConfirmItem == null) return;

        // Use ClickType.LEFT as the standard "confirm" click for placeholder/action resolution
        List<String> actionsToExecute = cachedConfirmItem.getActionsForClick(ClickType.LEFT);
        if (actionsToExecute == null || actionsToExecute.isEmpty()) return;

        Map<String, String> actionPlaceholders = new HashMap<>();
        actionPlaceholders.put("player", viewer.getName());
        actionPlaceholders.put("amount", amountFormatted);

        itemAction.executeAll(actionsToExecute, viewer, actionPlaceholders);
    }

    public void markSaleProcessed(UUID player) {
        processedSales.add(player);
        plugin.scheduler().runDelayed(
                () -> processedSales.remove(player),
                20L
        );
    }

    public boolean consumeProcessedSale(UUID player) {
        return processedSales.remove(player);
    }

    public void unregisterListeners() {
        HandlerList.unregisterAll(this.listener);
    }

    @Override
    public boolean owns(Inventory inv) {
        return inv.getHolder() instanceof SellHolder;
    }

    public GuiDefinition definition() { return definition; }

    public record SellHolder(UUID owner) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}