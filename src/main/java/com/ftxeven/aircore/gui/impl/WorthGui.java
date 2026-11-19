package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.BaseWorthGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.worth.WorthCalculator;
import com.ftxeven.aircore.service.ServiceManager;

import java.util.List;
import java.util.function.Supplier;

public final class WorthGui extends BaseWorthGui {

    public static final String ROLE = "worth";

    public WorthGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis) {
        super(configs, services, economy, guis);
    }

    @Override
    protected List<WorthCalculator.Entry> visibleEntries(GuiSession session, List<WorthCalculator.Entry> catalog) {
        return catalog;
    }
}