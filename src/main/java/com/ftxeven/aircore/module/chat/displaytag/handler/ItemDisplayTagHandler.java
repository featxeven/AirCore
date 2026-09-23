package com.ftxeven.aircore.module.chat.displaytag.handler;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickContext;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagHandler;
import com.ftxeven.aircore.util.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ItemDisplayTagHandler implements DisplayTagHandler {

    private final Supplier<LangConfig> lang;

    public ItemDisplayTagHandler(Supplier<LangConfig> lang) {
        this.lang = lang;
    }

    @Override
    public boolean guiRequired() {
        return false;
    }

    @Override
    public boolean available(Player sender) {
        return !heldItem(sender).getType().isAir();
    }

    @Override
    public void contribute(Player sender, Map<String, String> placeholders) {
        ItemStack item = heldItem(sender);
        ItemDisplay.formatInto(placeholders, "item", item, lang.get());
        placeholders.put("amount", String.valueOf(item.getAmount()));
    }

    @Override
    public Optional<DisplayTagClickContext> click(Player sender) {
        ItemStack item = heldItem(sender);
        return item.getType().isAir() ? Optional.empty() : Optional.of(new DisplayTagClickContext.ItemPreview(item.clone()));
    }

    private ItemStack heldItem(Player sender) {
        return sender.getInventory().getItemInMainHand();
    }
}