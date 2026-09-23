package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.database.query.HomeQuery;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.service.ServiceManager;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Supplier;

public final class HomeSearchGui extends BaseHomeGui {

    public static final String ROLE = "homes-search";

    public HomeSearchGui(ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, PluginGuiManager guis) {
        super(configs, services, homes, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, services.players());
        installFlags(viewer, session, null);
        String query = session.attribute(ATTR_SEARCH_QUERY, String.class);
        session.placeholders().put("query", query != null ? query : "");

        LayoutConfig layout = layout(session);
        UUID owner = owner(viewer, session);
        int pageSize = layout.homeSlots().size();
        if (pageSize == 0) {
            return;
        }

        ClampedPage clamped = queryClamped(session.page(), page ->
                homes.get().query(buildQuery(session, owner, pageSize, page)));
        session.page(clamped.page());

        int totalOwned = homes.get().count(owner);
        writePageResult(session, clamped.result(), totalOwned);

        HomeQuery facetsQuery = buildQuery(session, owner, pageSize, clamped.page())
                .withoutWorld().withoutIcons();
        writeFilterCyclers(session, layout.filters(), () -> homes.get().facets(facetsQuery));
        writeSortCycler(session, layout.sorts());
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        LayoutConfig layout = layout(session);
        UUID owner = owner(viewer, session);
        int pageSize = layout.homeSlots().size();
        if (pageSize == 0) {
            return;
        }

        PageResult<Home> pageResult = homes.get().query(buildQuery(session, owner, pageSize, session.page()));
        drawEntries(viewer, session, homeEntries(viewer, session, owner, pageResult.items()), layout.homeSlots().iterator());
    }

    private HomeQuery buildQuery(GuiSession session, UUID owner, int pageSize, int page) {
        HomeQuery.Builder builder = homeQueryBuilder(session, owner, pageSize, page);
        String search = session.attribute(ATTR_SEARCH_QUERY, String.class);
        if (search != null && !search.isBlank()) {
            builder.search(search);
        }
        return builder.build();
    }
}