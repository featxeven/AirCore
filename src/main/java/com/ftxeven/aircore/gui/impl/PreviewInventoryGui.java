package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.ContainerRenderer;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Iterator;

public final class PreviewInventoryGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "display-tag-inventory";
    public static final String ATTR_CONTENTS = "preview-contents";
    public static final String ATTR_ARMOR = "preview-armor";
    public static final String ATTR_OFFHAND = "preview-offhand";

    private final PlayerService players;
    private final LayoutGuiRegistry layouts;

    public PreviewInventoryGui(PlayerService players, LayoutGuiRegistry layouts) {
        this.players = players;
        this.layouts = layouts;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, players);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        LayoutConfig layout = layouts.layout(session.definition());

        ItemStack[] contents = session.attribute(ATTR_CONTENTS, ItemStack[].class);
        if (contents != null) {
            ContainerRenderer.draw(session, hotbarOf(contents), layout.hotbarSlots());
            ContainerRenderer.draw(session, storageOf(contents), layout.storageSlots());
        }

        ItemStack[] armor = session.attribute(ATTR_ARMOR, ItemStack[].class);
        if (armor != null) {
            ContainerRenderer.draw(session, armor, layout.armorSlots());
        }

        ItemStack offhand = session.attribute(ATTR_OFFHAND, ItemStack.class);
        Iterator<Integer> offhandSlot = layout.offhandSlots().iterator();
        if (offhandSlot.hasNext()) {
            ContainerRenderer.draw(session, offhand, offhandSlot.next());
        }
    }

    private static ItemStack[] hotbarOf(ItemStack[] contents) {
        return Arrays.copyOfRange(contents, 0, Math.min(9, contents.length));
    }

    private static ItemStack[] storageOf(ItemStack[] contents) {
        return contents.length > 9 ? Arrays.copyOfRange(contents, 9, contents.length) : new ItemStack[0];
    }
}