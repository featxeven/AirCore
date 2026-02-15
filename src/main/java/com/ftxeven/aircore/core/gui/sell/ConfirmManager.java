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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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

        Inventory inv = Bukkit.createInventory(new ConfirmHolder(sellInventory, result, isAll),
                definition.rows() * 9, mm.deserialize("<!italic>" + title));

        SellSlotMapper.fillConfirm(inv, definition, player, placeholders);
        sellManager.markTransitioning(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfirmHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        ItemStack clickedStack = event.getCurrentItem();
        if (clickedStack == null || clickedStack.getType().isAir()) return;

        Player player = (Player) event.getWhoClicked();
        String totalFormatted = plugin.economy().formats().formatAmount(holder.result().total());
        Map<String, String> placeholders = Map.of("total", totalFormatted);

        if (isKeyAtSlot(slot, "confirm")) {
            GuiDefinition.GuiItem item = definition.items().get("confirm");
            if (item != null) {
                if (plugin.gui().cooldowns().isOnCooldown(player, item)) {
                    plugin.gui().cooldowns().sendCooldownMessage(player, item);
                    return;
                }
                sellManager.processSale(player, holder.sellInventory, holder.result, holder.isAll);
                executeKeyActions("confirm", player, event.getClick(), placeholders);
            }
            return;
        }

        if (isKeyAtSlot(slot, "cancel")) {
            GuiDefinition.GuiItem item = definition.items().get("cancel");
            if (item != null) {
                if (plugin.gui().cooldowns().isOnCooldown(player, item)) {
                    plugin.gui().cooldowns().sendCooldownMessage(player, item);
                    return;
                }
                sellManager.markTransitioning(player.getUniqueId());
                player.openInventory(holder.sellInventory);
                executeKeyActions("cancel", player, event.getClick(), placeholders);
            }
            return;
        }

        handleGenericItem(slot, clickedStack, player, event.getClick(), placeholders);
    }

    private void handleGenericItem(int slot, ItemStack clicked, Player viewer, org.bukkit.event.inventory.ClickType click, Map<String, String> ph) {
        GuiDefinition.GuiItem def = findDefinitionAt(slot, clicked);
        if (def != null) {
            sellManager.handleAction(def, viewer, click, ph);
        }
    }

    private GuiDefinition.GuiItem findDefinitionAt(int slot, ItemStack clicked) {
        for (GuiDefinition.GuiItem item : definition.items().values()) {
            if (item.slots().contains(slot) && clicked.getType() == item.material()) {
                return item;
            }
        }
        return null;
    }

    private void executeKeyActions(String key, Player player, org.bukkit.event.inventory.ClickType click, Map<String, String> ph) {
        GuiDefinition.GuiItem item = definition.items().get(key);
        if (item != null) sellManager.handleAction(item, player, click, ph);
    }

    private boolean isKeyAtSlot(int slot, String key) {
        GuiDefinition.GuiItem gi = definition.items().get(key);
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

        if (plugin.gui().isReloading()) {
            sellManager.returnItems(player, holder.sellInventory());
            return;
        }

        if (!sellManager.isTransitioning(player.getUniqueId())) {
            sellManager.returnItems(player, holder.sellInventory());
        }
    }

    public GuiDefinition getDefinition() {
        return this.definition;
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public record ConfirmHolder(Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(null, 9); }
    }
}