package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class ConfirmManager implements Listener {

    private final AirCore plugin;
    private final SellManager sellManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;

    public ConfirmManager(AirCore plugin, SellManager sellManager) {
        this.plugin = plugin;
        this.sellManager = sellManager;
        loadDefinition();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/sell/confirm.yml");
        if (!file.exists()) plugin.saveResource("guis/sell/confirm.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadSection(cfg.getConfigurationSection("buttons"), items);
        loadSection(cfg.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(cfg.getString("title", "Confirm"), cfg.getInt("rows", 3), items, cfg);
    }

    private void loadSection(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) items.put(key, GuiItem.fromSection(key, itemSec));
        }
    }

    public void open(Player player, Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("total", plugin.economy().formats().formatAmount(result.total()));
        ph.put("total-raw", String.valueOf(result.total()));

        ConfirmHolder holder = new ConfirmHolder(definition, sellInventory, result, isAll, ph);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + PlaceholderUtil.apply(player, definition.title(), ph)));
        holder.setInventory(inv);

        SellSlotMapper.fillConfirm(plugin, inv, definition, player, ph);
        sellManager.markTransitioning(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmHolder holder)) return;
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked.equals(event.getView().getBottomInventory())) return;

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();

        GuiItem actionItem = null;
        for (String key : List.of("confirm", "cancel")) {
            GuiItem item = definition.items().get(key);
            if (item != null && item.slots().contains(slot)) {
                actionItem = item;
                break;
            }
        }

        if (actionItem != null) {
            handleConfirmOrCancel(player, actionItem, holder, event);
            return;
        }

        for (GuiItem item : definition.items().values()) {
            if (item.slots().contains(slot)) {
                sellManager.handleAction(item, player, event.getClick(), holder.placeholders(), holder.sellInventory());
                return;
            }
        }
    }

    private void handleConfirmOrCancel(Player player, GuiItem item, ConfirmHolder holder, InventoryClickEvent event) {
        List<String> actions = item.getActionsForClick(player, holder.placeholders(), event.getClick());
        boolean shouldClose = actions.stream().anyMatch(a -> a.equalsIgnoreCase("[close]"));

        if (item.key().equals("confirm")) {
            sellManager.processSale(player, holder.sellInventory(), holder.result(), holder.isAll());

            for (String action : actions) {
                if (action.equalsIgnoreCase("[sell]") || action.equalsIgnoreCase("[close]")) continue;
                sellManager.getActionProcessor().execute(action, player, holder.placeholders());
            }
            finalizeNavigation(player, holder, shouldClose);
        } else {
            sellManager.markTransitioning(player.getUniqueId());
            sellManager.handleAction(item, player, event.getClick(), holder.placeholders(), holder.sellInventory());
            player.openInventory(holder.sellInventory());
        }
    }

    private void finalizeNavigation(Player player, ConfirmHolder holder, boolean shouldClose) {
        if (shouldClose) {
            player.closeInventory();
        } else {
            sellManager.markTransitioning(player.getUniqueId());
            player.openInventory(holder.sellInventory());
            sellManager.refreshConfirmButton(holder.sellInventory(), player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfirmHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmHolder holder)) return;
        Player player = (Player) event.getPlayer();
        if (plugin.gui().isReloading() || sellManager.isTransitioning(player.getUniqueId()) || sellManager.consumeProcessedSale(player.getUniqueId())) return;

        sellManager.returnItems(player, holder.sellInventory());
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class ConfirmHolder implements InventoryHolder {
        private final GuiDefinition definition;
        private final Inventory sellInventory;
        private final SellSlotMapper.WorthResult result;
        private final boolean isAll;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public ConfirmHolder(GuiDefinition def, Inventory sellInv, SellSlotMapper.WorthResult res, boolean all, Map<String, String> ph) {
            this.definition = def; this.sellInventory = sellInv; this.result = res; this.isAll = all; this.placeholders = ph;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public GuiDefinition definition() { return definition; }
        public Inventory sellInventory() { return sellInventory; }
        public SellSlotMapper.WorthResult result() { return result; }
        public boolean isAll() { return isAll; }
        public Map<String, String> placeholders() { return placeholders; }
    }
}