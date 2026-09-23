package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.gui.render.RemoteContainerRenderer;
import com.ftxeven.aircore.service.inventory.InventoryHandle;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.logging.Logger;

public final class LiveEnderseeGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "inventory-endersee";
    public static final String ATTR_HANDLE = "endersee-handle";
    public static final String ATTR_INITIAL_CONTENTS = "endersee-initial-contents";
    public static final String ATTR_CAN_MODIFY = "endersee-can-modify";
    private static final String ATTR_CONTAINER = "endersee-container";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;
    private final Logger logger;

    public LiveEnderseeGui(PlayerService players, LayoutGuiRegistry layouts, Logger logger) {
        this.players = players;
        this.layouts = layouts;
        this.logger = logger;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, players);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        RemoteContainerRenderer container = session.attribute(ATTR_CONTAINER, RemoteContainerRenderer.class);
        if (container != null) {
            session.attachLiveContainer(container);
            container.repaint(session);
            return;
        }

        InventoryHandle handle = session.attribute(ATTR_HANDLE, InventoryHandle.class);
        ItemStack[] initialContents = session.attribute(ATTR_INITIAL_CONTENTS, ItemStack[].class);
        if (handle == null || initialContents == null) {
            logger.warning("GUI '" + session.definition().id() + "' opened for " + viewer.getName() + " without an InventoryHandle attached");
            return;
        }
        boolean canModify = Boolean.TRUE.equals(session.attribute(ATTR_CAN_MODIFY, Boolean.class));

        LayoutConfig layout = layouts.layout(session.definition());
        container = new RemoteContainerRenderer(handle, logger, canModify, List.of(
                RemoteContainerRenderer.Region.sequential(layout.enderchestSlots(), 0)
        ));

        session.attribute(ATTR_CONTAINER, container);
        session.attachLiveContainer(container);
        container.initialize(session, initialContents);
    }
}