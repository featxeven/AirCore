package com.ftxeven.aircore.module.chat.displaytag;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public sealed interface DisplayTagClickContext {

    record ItemPreview(ItemStack item) implements DisplayTagClickContext {
    }

    record InventoryPreview(ItemStack[] contents, ItemStack[] armor, @Nullable ItemStack offhand) implements DisplayTagClickContext {
    }

    record EnderChestPreview(ItemStack[] contents) implements DisplayTagClickContext {
    }

    record ShulkerPreview(ItemStack shulkerItem, ItemStack[] contents) implements DisplayTagClickContext {
    }
}