package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.homes.HomeManager;
import com.ftxeven.aircore.core.gui.invsee.enderchest.EnderchestManager;
import com.ftxeven.aircore.core.gui.invsee.inventory.InventoryManager;
import com.ftxeven.aircore.core.gui.sell.SellManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public final class GuiManager {
    private final AirCore plugin;
    private final ItemAction itemAction;
    private final CooldownManager cooldownManager;
    private final Map<String, CustomGuiManager> managers = new HashMap<>();
    private boolean reloading = false;

    public GuiManager(AirCore plugin) {
        this.plugin = plugin;
        this.itemAction = new ItemAction(plugin);
        this.cooldownManager = new CooldownManager(plugin);
        loadAll();
    }

    public void reload() {
        reloading = true;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (managers.values().stream().anyMatch(m -> m.owns(top))) {
                p.closeInventory();
            }
        }

        cooldownManager.clear();

        managers.values().forEach(CustomGuiManager::cleanup);
        managers.clear();
        loadAll();

        reloading = false;
    }

    public CooldownManager cooldowns() {
        return cooldownManager;
    }

    public boolean isReloading() { return reloading; }

    public void loadAll() {
        reg("inventory", new InventoryManager(plugin, itemAction));
        reg("enderchest", new EnderchestManager(plugin, itemAction));
        reg("sell", new SellManager(plugin, itemAction));
        reg("homes", new HomeManager(plugin, itemAction));
    }

    public void reg(String id, CustomGuiManager m) { managers.put(id.toLowerCase(), m); }

    public void openGui(String id, Player p, Map<String, String> ph) {
        if (reloading) return;

        CustomGuiManager m = managers.get(id.toLowerCase());
        if (m != null) {
            Inventory inv = m.build(p, ph);
            if (inv != null) p.openInventory(inv);
        }
    }

    public void handleClick(InventoryClickEvent e) {
        if (reloading || !(e.getWhoClicked() instanceof Player p)) return;

        managers.values().stream()
                .filter(m -> m.owns(e.getInventory()))
                .findFirst()
                .ifPresent(m -> m.handleClick(e, p));
    }

    public void refresh(Player player, Inventory inv, Map<String, String> placeholders) {
        if (inv == null || inv.getHolder() == null) return;

        for (CustomGuiManager manager : managers.values()) {
            if (manager.owns(inv)) {
                manager.refresh(inv, player, placeholders);
                break;
            }
        }
    }

    public CustomGuiManager getManager(String id) { return managers.get(id.toLowerCase()); }

    public EnderchestManager getEnderchestManager() { return (EnderchestManager) managers.get("enderchest"); }

    public interface CustomGuiManager {
        Inventory build(Player viewer, Map<String, String> placeholders);
        void handleClick(InventoryClickEvent event, Player viewer);
        boolean owns(Inventory inv);

        default void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {}

        default void cleanup() {}
    }
}