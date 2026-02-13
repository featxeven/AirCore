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

        String title = cfg.getString("title", "Confirm for $%total%");
        int rows = cfg.getInt("rows", 3);

        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        ConfigurationSection buttonsSec = cfg.getConfigurationSection("buttons");
        if (buttonsSec != null) {
            for (String key : buttonsSec.getKeys(false)) {
                ConfigurationSection sec = buttonsSec.getConfigurationSection(key);
                if (sec == null) continue;
                items.put(key, GuiDefinition.GuiItem.fromSection(key, sec, mm));
            }
        }

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                if (items.containsKey(key)) continue;

                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec == null) continue;
                items.put(key, GuiDefinition.GuiItem.fromSection(key, sec, mm));
            }
        }

        this.definition = new GuiDefinition(title, rows, items, cfg);
    }

    public void open(Player player, Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll) {
        double finalAmount = result.total();
        String totalFormatted = plugin.economy().formats().formatAmount(finalAmount);

        Map<String, String> placeholders = Map.of("total", totalFormatted);

        String rawTitle = PlaceholderUtil.apply(player, definition.title());
        String title = rawTitle.replace("%total%", totalFormatted).replace("%player%", player.getName());

        Inventory inv = Bukkit.createInventory(new ConfirmHolder(sellInventory, result, isAll),
                definition.rows() * 9, mm.deserialize(title));

        SellSlotMapper.fillConfirm(inv, definition, player, placeholders);

        sellManager.markTransitioning(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfirmHolder holder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        GuiDefinition.GuiItem clickedItem = null;
        String clickedKey = null;

        for (var entry : definition.items().entrySet()) {
            if (entry.getValue().slots().contains(slot)) {
                clickedItem = entry.getValue();
                clickedKey = entry.getKey();
                break;
            }
        }

        if (clickedItem == null) return;

        String formattedTotal = plugin.economy().formats().formatAmount(holder.result().total());

        sellManager.executeItemActions(clickedItem, player, event.getClick(), Map.of("total", formattedTotal));

        if ("confirm".equalsIgnoreCase(clickedKey)) {
            sellManager.processSale(player, holder.sellInventory, holder.result, holder.isAll);

            boolean closeOnSell = definition.config().getBoolean("close-on-sell", true);

            if (closeOnSell) {
                player.closeInventory();
            } else {
                sellManager.markTransitioning(player.getUniqueId());
                player.openInventory(holder.sellInventory);
                sellManager.refreshConfirmButton(holder.sellInventory, player);
            }
        }
        else if ("cancel".equalsIgnoreCase(clickedKey)) {
            sellManager.markTransitioning(player.getUniqueId());
            player.openInventory(holder.sellInventory);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
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

    public record ConfirmHolder(Inventory sellInventory, SellSlotMapper.WorthResult result, boolean isAll) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(null, 9); }
    }
}