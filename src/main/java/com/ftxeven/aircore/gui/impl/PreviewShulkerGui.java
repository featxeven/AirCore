package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.render.ContainerRenderer;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class PreviewShulkerGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "display-tag-shulker";
    public static final String ATTR_SHULKER_ITEM = "preview-shulker-item";
    public static final String ATTR_CONTENTS = "preview-contents";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;
    private final Supplier<LangConfig> lang;

    public PreviewShulkerGui(PlayerService players, LayoutGuiRegistry layouts, Supplier<LangConfig> lang) {
        this.players = players;
        this.layouts = layouts;
        this.lang = lang;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, players);
        ItemStack shulkerItem = session.attribute(ATTR_SHULKER_ITEM, ItemStack.class);
        if (shulkerItem != null) {
            ItemDisplay.formatInto(session.placeholders(), "item", shulkerItem, lang.get());
        }
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        ItemStack[] contents = session.attribute(ATTR_CONTENTS, ItemStack[].class);
        if (contents != null) {
            ContainerRenderer.draw(session, contents, layouts.layout(session.definition()).shulkerSlots());
        }
    }
}