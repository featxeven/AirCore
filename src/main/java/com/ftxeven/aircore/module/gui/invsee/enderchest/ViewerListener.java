package com.ftxeven.aircore.module.gui.invsee.enderchest;

import com.ftxeven.aircore.module.gui.GuiDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class ViewerListener implements Listener {

    private final EnderchestManager manager;

    public ViewerListener(EnderchestManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderchestManager.EnderchestHolder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        if (!viewer.hasPermission("aircore.command.enderchest.others.modify")) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getInventory();
        int topSize = top.getSize();
        GuiDefinition def = manager.definition();

        boolean affectedTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) continue;
            affectedTop = true;
            if (!EnderchestSlotMapper.isDynamicSlot(def, rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        if (affectedTop) manager.syncAndRefresh(top, viewer);
    }
}