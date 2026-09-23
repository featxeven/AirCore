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

import java.util.Set;
import java.util.function.Supplier;

public final class PreviewItemGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "display-tag-item";
    public static final String ATTR_ITEM = "preview-item";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;
    private final Supplier<LangConfig> lang;

    public PreviewItemGui(PlayerService players, LayoutGuiRegistry layouts, Supplier<LangConfig> lang) {
        this.players = players;
        this.layouts = layouts;
        this.lang = lang;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, players);
        ItemStack item = session.attribute(ATTR_ITEM, ItemStack.class);
        if (item != null) {
            ItemDisplay.formatInto(session.placeholders(), "item", item, lang.get());
            session.placeholders().put("amount", String.valueOf(item.getAmount()));
        }
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        ItemStack item = session.attribute(ATTR_ITEM, ItemStack.class);
        Set<Integer> slots = layouts.layout(session.definition()).itemSlots();
        if (item != null) {
            ContainerRenderer.draw(session, item, slots);
        }
    }
}