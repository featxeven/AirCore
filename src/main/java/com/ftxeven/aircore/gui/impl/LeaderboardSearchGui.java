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

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class LeaderboardSearchGui extends BaseLeaderboardGui {

    public static final String ROLE = "leaderboards-search";

    public LeaderboardSearchGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis, Logger logger) {
        super(configs, services, economy, guis, logger);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        String query = session.attribute(ATTR_SEARCH_QUERY, String.class);
        session.placeholders().put("query", query != null ? query : "");

        String id = boardId(session);
        if (id == null) {
            return;
        }
        Snapshot snapshot = writeBoardPlaceholders(viewer, session, id);
        int pageSize = layout(session).entrySlots().size();
        if (pageSize == 0) {
            return;
        }
        writePage(session, filtered(snapshot, query), pageSize);
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
        String query = session.attribute(ATTR_SEARCH_QUERY, String.class);
        Snapshot snapshot = services.leaderboards().snapshot(id);
        PageResult<Entry> page = writePage(session, filtered(snapshot, query), pageSize);
        drawEntries(viewer, session, entries(viewer, session, page.items()));
    }

    private List<Entry> filtered(Snapshot snapshot, String query) {
        if (query == null || query.isBlank()) {
            return snapshot.entries();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return snapshot.entries().stream().filter(entry -> matches(entry, needle)).toList();
    }

    private boolean matches(Entry entry, String needle) {
        String name = entry.holder().name();
        String nickname = entry.holder().nickname();
        return (name != null && name.toLowerCase(Locale.ROOT).contains(needle))
                || (nickname != null && nickname.toLowerCase(Locale.ROOT).contains(needle));
    }
}