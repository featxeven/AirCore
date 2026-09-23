package com.ftxeven.aircore.module.chat.displaytag.handler;

import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickContext;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class InventoryDisplayTagHandler implements DisplayTagHandler {

    @Override
    public boolean available(Player sender) {
        return true; // always previewable, even when empty
    }

    @Override
    public void contribute(Player sender, Map<String, String> placeholders) {
        // no extra placeholders needed
    }

    @Override
    public Optional<DisplayTagClickContext> click(Player sender) {
        PlayerInventory inventory = sender.getInventory();
        return Optional.of(new DisplayTagClickContext.InventoryPreview(
                snapshot(inventory.getContents()),
                snapshot(inventory.getArmorContents()),
                cloneOrNull(inventory.getItemInOffHand())));
    }

    private ItemStack[] snapshot(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = cloneOrNull(contents[i]);
        }
        return copy;
    }

    private @Nullable ItemStack cloneOrNull(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir() ? item.clone() : null;
    }
}