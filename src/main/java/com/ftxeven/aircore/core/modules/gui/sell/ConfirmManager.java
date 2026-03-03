package com.ftxeven.aircore.core.modules.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.modules.gui.invsee.inventory.InventorySlotMapper;
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
        String formatted = plugin.economy().formats().formatAmount(result.total());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("total", formatted);
        placeholders.put("total-raw", String.valueOf(result.total()));

        String title = PlaceholderUtil.apply(player, definition.title(), placeholders);

        ConfirmHolder holder = new ConfirmHolder(definition, sellInventory, result, isAll, placeholders);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        SellSlotMapper.fillConfirm(inv, definition, player, placeholders);

        sellManager.markTransitioning(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmHolder holder)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
        } else {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();
        GuiItem item = InventorySlotMapper.findItem(definition, slot);
        if (item == null) return;

        if (item.key().equals("confirm")) {
            sellManager.markTransitioning(player.getUniqueId());
            handleButtonAction(player, item, () ->
                            sellManager.processSale(player, holder.sellInventory(), holder.result(), holder.isAll()),
                    holder.placeholders(), event.getClick(), true);
        } else if (item.key().equals("cancel")) {
            sellManager.markTransitioning(player.getUniqueId());
            handleButtonAction(player, item, () -> player.openInventory(holder.sellInventory()),
                    holder.placeholders(), event.getClick(), false);
        } else {
            sellManager.handleAction(item, player, event.getClick(), holder.placeholders());
        }
    }

    private void handleButtonAction(Player player, GuiItem item, Runnable logic, Map<String, String> ph, org.bukkit.event.inventory.ClickType click, boolean runLogicFirst) {
        if (plugin.gui().cooldowns().isOnCooldown(player, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(player, item);
            return;
        }

        if (runLogicFirst) {
            logic.run();
            sellManager.handleAction(item, player, click, ph);
        } else {
            sellManager.handleAction(item, player, click, ph);
            logic.run();
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
        UUID uuid = player.getUniqueId();

        if (plugin.gui().isReloading() || sellManager.isTransitioning(uuid) || sellManager.consumeProcessedSale(uuid)) {
            return;
        }

        sellManager.returnItems(player, holder.sellInventory());
    }

    public GuiDefinition getDefinition() { return this.definition; }
    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class ConfirmHolder implements InventoryHolder {
        private final GuiDefinition definition;
        private final Inventory sellInventory;
        private final SellSlotMapper.WorthResult result;
        private final boolean isAll;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public ConfirmHolder(GuiDefinition definition, Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll, Map<String, String> placeholders) {
            this.definition = definition;
            this.sellInventory = sellInventory;
            this.result = result;
            this.isAll = isAll;
            this.placeholders = placeholders;
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