package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.gui.BaseLeaderboardGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.LeaderboardService.Entry;
import com.ftxeven.aircore.service.LeaderboardService.Snapshot;
import org.bukkit.entity.Player;

import java.util.function.Supplier;
import java.util.logging.Logger;

public final class LeaderboardBrowserGui extends BaseLeaderboardGui {

    public static final String ROLE = "leaderboards-browser";

    public LeaderboardBrowserGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis, Logger logger) {
        super(configs, services, economy, guis, logger);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        String id = boardId(session);
        if (id == null) {
            return;
        }
        Snapshot snapshot = writeBoardPlaceholders(viewer, session, id);
        int pageSize = layout(session).entrySlots().size();
        if (pageSize == 0) {
            return;
        }
        writePage(session, snapshot.entries(), pageSize);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        String id = boardId(session);
        if (id == null) {
            return;
        }
        int pageSize = layout(session).entrySlots().size();
        if (pageSize == 0) {
            return;
        }
        Snapshot snapshot = services.leaderboards().snapshot(id);
        PageResult<Entry> page = writePage(session, snapshot.entries(), pageSize);
        drawEntries(viewer, session, entries(viewer, session, page.items()));
    }
}