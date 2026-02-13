package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.core.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        guiManager.handleClick(event);
    }
}
