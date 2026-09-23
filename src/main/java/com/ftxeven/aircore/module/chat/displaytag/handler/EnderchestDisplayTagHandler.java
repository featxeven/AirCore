package com.ftxeven.aircore.module.chat.displaytag.handler;

import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickContext;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class EnderchestDisplayTagHandler implements DisplayTagHandler {

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
        return Optional.of(new DisplayTagClickContext.EnderChestPreview(snapshot(sender.getEnderChest().getContents())));
    }

    private ItemStack[] snapshot(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return copy;
    }
}