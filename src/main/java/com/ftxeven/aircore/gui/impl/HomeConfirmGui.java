package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.service.ServiceManager;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class HomeConfirmGui extends BaseHomeGui {

    public static final String ROLE = "homes-confirm";

    public HomeConfirmGui(ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, PluginGuiManager guis) {
        super(configs, services, homes, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, services.players());
        resolveTargetHome(viewer, session);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        drawTargetHome(viewer, session);
    }
}