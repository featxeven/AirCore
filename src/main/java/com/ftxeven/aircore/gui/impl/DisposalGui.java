package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.BaseHoldingGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.item.HoldingKind;
import org.bukkit.entity.Player;

import java.util.Map;

public final class DisposalGui extends BaseHoldingGui {

    public static final String ROLE = "disposal";
    public static final String CONFIRM_ROLE = "disposal-confirm";

    public DisposalGui(ServiceManager services, PluginGuiManager guis) {
        super(services, guis, HoldingKind.DISPOSAL);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        writeAmounts(viewer, session.placeholders());
        session.flagResolver(GuiFlags.forDisposal(viewer, services.holding()));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        LayoutConfig layout = guis.layouts().layout(session.definition());
        attachSlots(viewer, session, layout.disposalSlots());
    }

    private void writeAmounts(Player viewer, Map<String, String> placeholders) {
        int selected = heldCount(viewer);
        int owned = count(ownedStorage(viewer));
        placeholders.put("amount", String.valueOf(selected));
        placeholders.put("amount_all", String.valueOf(selected + owned));
    }
}