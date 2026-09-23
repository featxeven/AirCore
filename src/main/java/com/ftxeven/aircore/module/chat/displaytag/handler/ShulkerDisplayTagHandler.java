package com.ftxeven.aircore.module.chat.displaytag.handler;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickContext;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagHandler;
import com.ftxeven.aircore.util.ItemDisplay;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ShulkerDisplayTagHandler implements DisplayTagHandler {

    private final Supplier<LangConfig> lang;

    public ShulkerDisplayTagHandler(Supplier<LangConfig> lang) {
        this.lang = lang;
    }

    @Override
    public boolean available(Player sender) {
        return isShulkerBox(heldItem(sender));
    }

    @Override
    public void contribute(Player sender, Map<String, String> placeholders) {
        ItemStack item = heldItem(sender);
        ItemDisplay.formatInto(placeholders, "item", item, lang.get());
    }

    @Override
    public Optional<DisplayTagClickContext> click(Player sender) {
        ItemStack item = heldItem(sender);
        if (!isShulkerBox(item)) {
            return Optional.empty();
        }
        return Optional.of(new DisplayTagClickContext.ShulkerPreview(item.clone(), snapshot(contentsOf(item))));
    }

    private ItemStack heldItem(Player sender) {
        return sender.getInventory().getItemInMainHand();
    }

    private boolean isShulkerBox(ItemStack item) {
        return !item.getType().isAir()
                && item.getItemMeta() instanceof BlockStateMeta blockState
                && blockState.getBlockState() instanceof ShulkerBox;
    }

    private ItemStack[] contentsOf(ItemStack item) {
        BlockStateMeta blockState = (BlockStateMeta) item.getItemMeta();
        ShulkerBox shulker = (ShulkerBox) blockState.getBlockState();
        return shulker.getInventory().getContents();
    }

    private ItemStack[] snapshot(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return copy;
    }
}