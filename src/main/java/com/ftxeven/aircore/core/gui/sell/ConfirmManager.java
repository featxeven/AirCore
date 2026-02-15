package com.ftxeven.aircore.core.gui.sell;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
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
import java.util.HashMap;
import java.util.Map;

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
        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        loadSection(cfg.getConfigurationSection("buttons"), items);
        loadSection(cfg.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(cfg.getString("title", "Confirm"), cfg.getInt("rows", 3), items, cfg);
    }

    private void loadSection(ConfigurationSection sec, Map<String, GuiDefinition.GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) {
                items.put(key, GuiDefinition.GuiItem.fromSection(key, itemSec));
            }
        }
    }

    public void open(Player player, Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll) {
        double finalAmount = result.total();
        String totalFormatted = plugin.economy().formats().formatAmount(finalAmount);
        Map<String, String> placeholders = Map.of("total", totalFormatted);

        String title = PlaceholderUtil.apply(player, definition.title())
                .replace("%total%", totalFormatted)
                .replace("%player%", player.getName());

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

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            event.setCancelled(true);
        } else {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();
        GuiDefinition def = holder.definition();
        Map<String, String> placeholders = holder.placeholders();

        if (isKeyAtSlot(def, slot, "confirm")) {
            handleButtonAction(player, def.items().get("confirm"), () -> sellManager.processSale(player, holder.sellInventory(), holder.result(), holder.isAll()), placeholders, event.getClick());
            return;
        }

        if (isKeyAtSlot(def, slot, "cancel")) {
            handleButtonAction(player, def.items().get("cancel"), () -> {
                sellManager.markTransitioning(player.getUniqueId());
                player.openInventory(holder.sellInventory());
            }, placeholders, event.getClick());
            return;
        }

        for (GuiDefinition.GuiItem item : def.items().values()) {
            if (item.slots().contains(slot)) {
                sellManager.handleAction(item, player, event.getClick(), placeholders);
                break;
            }
        }
    }

    private void handleButtonAction(Player player, GuiDefinition.GuiItem item, Runnable logic, Map<String, String> ph, org.bukkit.event.inventory.ClickType click) {
        if (item == null) return;
        if (plugin.gui().cooldowns().isOnCooldown(player, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(player, item);
            return;
        }
        logic.run();
        sellManager.handleAction(item, player, click, ph);
    }

    private boolean isKeyAtSlot(GuiDefinition def, int slot, String key) {
        GuiDefinition.GuiItem gi = def.items().get(key);
        return gi != null && gi.slots().contains(slot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfirmHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmHolder holder)) return;
        Player player = (Player) event.getPlayer();

        if (plugin.gui().isReloading() || !sellManager.isTransitioning(player.getUniqueId())) {
            sellManager.returnItems(player, holder.sellInventory());
        }
    }

    public GuiDefinition getDefinition() {
        return this.definition;
    }

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