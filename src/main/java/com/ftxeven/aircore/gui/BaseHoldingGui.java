package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.render.HoldingContainerRenderer;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.item.HoldingKind;
import com.ftxeven.aircore.service.item.HoldingService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public abstract class BaseHoldingGui implements GuiRenderer.DynamicRenderer {

    private static final int MAX_SNAPSHOT_SIZE = 54;
    private static final String ATTR_CONTAINER = "holding-container";

    protected final ServiceManager services;
    protected final PluginGuiManager guis;
    private final HoldingKind kind;

    protected BaseHoldingGui(ServiceManager services, PluginGuiManager guis, HoldingKind kind) {
        this.services = services;
        this.guis = guis;
        this.kind = kind;
    }

    protected HoldingKind kind() {
        return kind;
    }

    protected HoldingService holding() {
        return services.holding();
    }

    protected HoldingContainerRenderer attachSlots(Player viewer, GuiSession session, Set<Integer> slots) {
        HoldingContainerRenderer container = session.attribute(ATTR_CONTAINER, HoldingContainerRenderer.class);
        if (container == null) {
            container = new HoldingContainerRenderer(viewer, guis.guis()::session, holding(), kind, List.copyOf(slots));
            session.attribute(ATTR_CONTAINER, container);
        }
        session.attachLiveContainer(container);
        container.initialize(session);
        return container;
    }

    protected int heldCount(Player viewer) {
        return holding().count(viewer.getUniqueId(), kind);
    }

    public static ItemStack[] ownedStorage(Player viewer) {
        return viewer.getInventory().getStorageContents();
    }

    public static int count(ItemStack[] items) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static ItemStack[] held(HoldingService holding, Player viewer, HoldingKind kind) {
        return holding.snapshot(viewer.getUniqueId(), kind, MAX_SNAPSHOT_SIZE);
    }

    public static ItemStack[] combined(ItemStack[] first, ItemStack[] second) {
        ItemStack[] merged = new ItemStack[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}