package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.core.gui.GuiDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ViewerListener implements Listener {

    private final InventoryManager invseeManager;

    public ViewerListener(InventoryManager inventoryManager) {
        this.invseeManager = inventoryManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InventoryManager.InvseeHolder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        if (!viewer.hasPermission("aircore.command.invsee.modify")) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getInventory();
        int topSize = top.getSize();
        GuiDefinition def = invseeManager.definition();

        boolean affectedTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) continue;

            affectedTop = true;
            if (!isValidDragSlot(rawSlot, event.getOldCursor(), def)) {
                event.setCancelled(true);
                return;
            }
        }

        if (affectedTop) {
            invseeManager.syncAndRefresh(top, viewer);
        }
    }

    private boolean isValidDragSlot(int rawSlot, ItemStack dragging, GuiDefinition def) {
        if (def == null || def.items() == null) return false;

        if (InventorySlotMapper.findItem(def, rawSlot) == null) return false;
        if (!InventorySlotMapper.isDynamicSlot(def, rawSlot)) return false;

        var armorItem = def.items().get("player-armor");
        if (armorItem != null && armorItem.slots() != null && armorItem.slots().contains(rawSlot)) {
            return invseeManager.isValidArmorForSlot(dragging, rawSlot);
        }
        return true;
    }
}