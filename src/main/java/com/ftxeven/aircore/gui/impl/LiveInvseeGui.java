package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.gui.render.PlayerInventorySlots;
import com.ftxeven.aircore.gui.render.RemoteContainerRenderer;
import com.ftxeven.aircore.service.inventory.InventoryHandle;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class LiveInvseeGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "inventory-invsee";
    public static final String ATTR_HANDLE = "invsee-handle";
    public static final String ATTR_INITIAL_CONTENTS = "invsee-initial-contents";
    public static final String ATTR_CAN_MODIFY = "invsee-can-modify";
    private static final String ATTR_CONTAINER = "invsee-container";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;
    private final Logger logger;

    public LiveInvseeGui(PlayerService players, LayoutGuiRegistry layouts, Logger logger) {
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
        List<RemoteContainerRenderer.Region> regions = new ArrayList<>();
        regions.addAll(armorRegions(layout, session.definition().id()));
        regions.add(RemoteContainerRenderer.Region.sequential(layout.offhandSlots(), PlayerInventorySlots.OFFHAND));
        regions.add(RemoteContainerRenderer.Region.sequential(layout.hotbarSlots(), PlayerInventorySlots.HOTBAR_START));
        regions.add(RemoteContainerRenderer.Region.sequential(layout.storageSlots(), PlayerInventorySlots.STORAGE_START));

        container = new RemoteContainerRenderer(handle, logger, canModify, regions);
        session.attribute(ATTR_CONTAINER, container);
        session.attachLiveContainer(container);
        container.initialize(session, initialContents);
    }

    private List<RemoteContainerRenderer.Region> armorRegions(LayoutConfig layout, String guiId) {
        List<Integer> guiSlots = List.copyOf(layout.armorSlots());
        if (guiSlots.isEmpty()) {
            return List.of();
        }
        if (guiSlots.size() != 4) {
            logger.warning("GUI '" + guiId + "' has " + guiSlots.size() + " armor-slots configured, expected exactly 4 "
                    + "(boots, leggings, chestplate, helmet) - armor slots will be inert");
            return List.of();
        }

        EquipmentSlot[] order = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
        int[] targetSlots = { PlayerInventorySlots.ARMOR_BOOTS, PlayerInventorySlots.ARMOR_LEGGINGS,
                PlayerInventorySlots.ARMOR_CHESTPLATE, PlayerInventorySlots.ARMOR_HELMET };

        List<RemoteContainerRenderer.Region> regions = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            EquipmentSlot expected = order[i];
            regions.add(RemoteContainerRenderer.Region.mapped(
                    List.of(guiSlots.get(i)), List.of(targetSlots[i]),
                    item -> RemoteContainerRenderer.isEquipmentType(item, expected)));
        }
        return regions;
    }
}