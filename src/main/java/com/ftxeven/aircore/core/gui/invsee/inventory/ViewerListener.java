package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class ViewerListener implements Listener {

    private final InventoryManager invseeManager;

    public ViewerListener(InventoryManager inventoryManager) {
        this.invseeManager = inventoryManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InventoryManager.InvseeHolder holder)) return;

        UUID targetUUID = holder.targetUUID();
        Player viewer = (Player) event.getWhoClicked();

        if (invseeManager.isPending(targetUUID)) {
            event.setCancelled(true);
            return;
        }

        int topSize = top.getSize();
        GuiDefinition def = invseeManager.definition();
        boolean affectedTop = false;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) continue;

            if (invseeManager.isSlotLocked(targetUUID, rawSlot)) {
                event.setCancelled(true);
                return;
            }

            affectedTop = true;
            if (!isValidDragSlot(rawSlot, event.getOldCursor(), def)) {
                event.setCancelled(true);
                return;
            }
        }

        if (affectedTop) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) invseeManager.lockSlot(targetUUID, rawSlot);
            }
            invseeManager.syncAndRefresh(top, viewer);
        }
    }

    private boolean isValidDragSlot(int slot, ItemStack dragging, GuiDefinition def) {
        if (InventorySlotMapper.findItem(def, slot) == null) return false;
        if (!InventorySlotMapper.isDynamicSlot(def, slot)) return false;

        GuiItem armorItem = def.items().get("armor-slots");
        if (armorItem != null && armorItem.slots().contains(slot)) {
            return invseeManager.isValidArmorForSlot(dragging, slot);
        }

        return true;
    }
}