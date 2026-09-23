package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.core.gui.render.RenderEntry;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.LeaderboardService.Entry;
import com.ftxeven.aircore.service.LeaderboardService.Snapshot;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public abstract class BaseLeaderboardGui implements GuiRenderer.DynamicRenderer {

    public static final String ATTR_SEARCH_QUERY = "leaderboard-search-query";
    public static final String ATTR_BOARD = "leaderboard-id";

    protected final ConfigManager configs;
    protected final ServiceManager services;
    protected final Supplier<EconomyModule> economy;
    protected final PluginGuiManager guis;
    protected final Logger logger;

    protected BaseLeaderboardGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis, Logger logger) {
        this.configs = configs;
        this.services = services;
        this.economy = economy;
        this.guis = guis;
        this.logger = logger;
    }

    protected LayoutConfig layout(GuiSession session) {
        return guis.layouts().layout(session.definition());
    }

    public static @Nullable String boardOf(GuiSession session, LayoutConfig layout) {
        String pinned = layout.leaderboard();
        return pinned != null ? pinned : session.attribute(ATTR_BOARD, String.class);
    }

    protected @Nullable String boardId(GuiSession session) {
        String id = boardOf(session, layout(session));
        if (id == null) {
            logger.warning("GUI '" + session.definition().id() + "' has no 'layout.leaderboard' configured and wasn't "
                    + "opened from a leaderboard screen, it will render empty");
            return null;
        }
        if (configs.leaderboards().leaderboard(id).isEmpty()) {
            logger.warning("GUI '" + session.definition().id() + "' shows leaderboard '" + id
                    + "', which isn't a configured leaderboard - it will render empty");
            return null;
        }
        return id;
    }

    // Header / pagination

    // %total% %interval% %viewer_rank% %viewer_value% for this board, from the viewer's own perspective
    protected Snapshot writeBoardPlaceholders(Player viewer, GuiSession session, String id) {
        Snapshot snapshot = services.leaderboards().snapshot(id);
        GuiPlaceholders.formatBoard(session.placeholders(), configs, snapshot, id, viewer.getUniqueId(), economy.get());
        return snapshot;
    }

    protected PageResult<Entry> writePage(GuiSession session, List<Entry> entries, int pageSize) {
        PageResult<Entry> page = PageResult.of(entries, session.page(), Math.max(1, pageSize));
        session.page(page.page());
        session.totalPages(page.totalPages());
        session.placeholders().put("current", String.valueOf(page.totalResults()));
        return page;
    }

    // Entry drawing

    protected List<RenderEntry> entries(Player viewer, GuiSession session, List<Entry> pageItems) {
        ItemConfig.Template base = layout(session).entry();
        if (base == null) {
            return List.of();
        }
        List<RenderEntry> rendered = new ArrayList<>(pageItems.size());
        for (Entry entry : pageItems) {
            Map<String, String> specific = new LinkedHashMap<>();
            GuiPlaceholders.formatEntry(specific, entry, services.players(), economy.get());
            rendered.add(new RenderEntry(base, null, entryPlaceholders(session, specific),
                    GuiFlags.builder(viewer).self(entry.holder().uuid()).build(),
                    entry.holder().uuid().toString()));
        }
        return rendered;
    }

    protected Map<String, String> entryPlaceholders(GuiSession session, Map<String, String> specific) {
        return GuiPlaceholders.combine(session, specific);
    }

    protected void drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries) {
        guis.guis().renderer().drawEntries(viewer, session, entries, layout(session).entrySlots());
    }
}