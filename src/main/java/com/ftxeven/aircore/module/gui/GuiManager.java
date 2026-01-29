package com.ftxeven.aircore.module.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.gui.enderchest.EnderchestManager;
import com.ftxeven.aircore.module.gui.invsee.InvseeManager;
import com.ftxeven.aircore.module.gui.sell.SellManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public final class GuiManager {
    private final AirCore plugin;
    private final ItemAction itemAction;
    private final Map<String, CustomGuiManager> customManagers = new HashMap<>();
    private boolean reloading = false;

    public GuiManager(AirCore plugin) {
        this.plugin = plugin;
        this.itemAction = new ItemAction(plugin);

        loadAll();
    }

    public boolean isReloading() { return reloading; }

    public void reload() {
        reloading = true;

        // Close all inventories first
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            for (CustomGuiManager mgr : customManagers.values()) {
                if (mgr.owns(open)) {
                    player.closeInventory();
                    break;
                }
            }
        }

        // Unregister listeners
        for (CustomGuiManager mgr : customManagers.values()) {
            if (mgr instanceof InvseeManager invsee) {
                invsee.unregisterListeners();
            } else if (mgr instanceof EnderchestManager enderchest) {
                enderchest.unregisterListeners();
            } else if (mgr instanceof SellManager sell) {
                sell.unregisterListeners();
            }
        }

        // Clear and reload managers
        customManagers.clear();
        loadAll();

        reloading = false;
    }

    public void loadAll() {
        register("invsee", new InvseeManager(plugin, itemAction));
        register("enderchest", new EnderchestManager(plugin, itemAction));
        register("sell", new SellManager(plugin, itemAction));
    }


    public void register(String id, CustomGuiManager manager) {
        customManagers.put(id.toLowerCase(), manager);
    }

    public void openGui(String id, Player viewer, Map<String,String> placeholders) {
        CustomGuiManager mgr = customManagers.get(id.toLowerCase());
        if (mgr != null) {
            Inventory inv = mgr.build(viewer, placeholders);
            if (inv != null) viewer.openInventory(inv);
        }
    }

    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        for (CustomGuiManager mgr : customManagers.values()) {
            if (mgr.owns(inv)) {
                mgr.handleClick(event, player);
                return;
            }
        }
    }

    public CustomGuiManager getManager(String id) {
        return customManagers.get(id.toLowerCase());
    }

    public interface CustomGuiManager {
        Inventory build(Player viewer, Map<String, String> placeholders);
        void handleClick(InventoryClickEvent event, Player viewer);
        boolean owns(Inventory inv);
    }
}
