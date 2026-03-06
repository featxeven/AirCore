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
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InventoryManager.InvseeHolder)) return;

        Player viewer = (Player) event.getWhoClicked();

        if (!viewer.hasPermission("aircore.command.invsee.modify")) {
            event.setCancelled(true);
            return;
        }

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

    private boolean isValidDragSlot(int slot, ItemStack dragging, GuiDefinition def) {
        if (InventorySlotMapper.findItem(def, slot) == null) return false;

        if (!InventorySlotMapper.isDynamicSlot(def, slot)) return false;

        var armorItem = def.items().get("armor-slots");
        if (armorItem != null && armorItem.slots().contains(slot)) {
            return invseeManager.isValidArmorForSlot(dragging, slot);
        }

        return true;
    }
}