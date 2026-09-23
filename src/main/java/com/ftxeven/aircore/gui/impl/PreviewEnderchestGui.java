package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.render.ContainerRenderer;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PreviewEnderchestGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "display-tag-enderchest";
    public static final String ATTR_CONTENTS = "preview-contents";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;

    public PreviewEnderchestGui(PlayerService players, LayoutGuiRegistry layouts) {
        this.players = players;
        this.layouts = layouts;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, players);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        ItemStack[] contents = session.attribute(ATTR_CONTENTS, ItemStack[].class);
        if (contents != null) {
            ContainerRenderer.draw(session, contents, layouts.layout(session.definition()).enderchestSlots());
        }
    }
}