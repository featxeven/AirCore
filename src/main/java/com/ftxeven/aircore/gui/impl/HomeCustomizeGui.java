package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.render.RenderEntry;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.service.ServiceManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class HomeCustomizeGui extends BaseHomeGui {

    public static final String ROLE = "homes-customize";

    public HomeCustomizeGui(ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, PluginGuiManager guis) {
        super(configs, services, homes, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        GuiPlaceholders.write(session, services.players());
        resolveTargetHome(viewer, session);

        LayoutConfig layout = layout(session);
        LayoutConfig.Cycler filters = layout.filters();
        List<FilterConfig.HomeIcon> filteredIcons = filteredIcons(session, filters);

        session.placeholders().put("total", String.valueOf(configs.filter().homeIcons().size()));
        session.placeholders().put("current", String.valueOf(filteredIcons.size()));

        int pageSize = layout.iconSlots().size();
        int totalPages = pageSize > 0 ? Math.max(1, (int) Math.ceil(filteredIcons.size() / (double) pageSize)) : 1;
        int page = Math.clamp(session.page(), 1, totalPages);
        session.page(page);
        session.totalPages(totalPages);

        writeIconFilterCycler(session, filters);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        drawTargetHome(viewer, session);
        drawIconPage(viewer, session);
    }

    private void drawIconPage(Player viewer, GuiSession session) {
        LayoutConfig layout = layout(session);
        ItemConfig.Template iconTemplate = layout.icon();
        int pageSize = layout.iconSlots().size();
        if (iconTemplate == null || pageSize == 0) {
            return;
        }

        UUID owner = owner(viewer, session);
        Home home = session.attribute(ATTR_TARGET_HOME, Home.class);

        List<FilterConfig.HomeIcon> filteredIcons = filteredIcons(session, layout.filters());
        int page = session.page();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, filteredIcons.size());
        List<FilterConfig.HomeIcon> pageIcons = from < to ? filteredIcons.subList(from, to) : List.of();

        List<RenderEntry> entries = new ArrayList<>(pageIcons.size());
        for (FilterConfig.HomeIcon icon : pageIcons) {
            Map<String, String> specific = new LinkedHashMap<>(GuiPlaceholders.icon(configs.filter(), icon));
            specific.putAll(GuiPlaceholders.iconGridState(icon, home, viewer));
            entries.add(new RenderEntry(overlayIcon(iconTemplate, icon.icon()), null, entryPlaceholders(session, specific),
                    GuiFlags.forIcon(viewer, configs.filter(), owner, home, icon, homes.get().homeResolver(owner))));
        }
        drawEntries(viewer, session, entries, layout.iconSlots());
    }

    private List<FilterConfig.HomeIcon> filteredIcons(GuiSession session, LayoutConfig.Cycler filters) {
        Set<String> icons = resolveIconFilter(session, filters).orElse(null);
        List<FilterConfig.HomeIcon> all = new ArrayList<>(configs.filter().homeIcons().values());
        return icons == null ? all : all.stream().filter(icon -> icons.contains(icon.id())).toList();
    }

    private void writeIconFilterCycler(GuiSession session, LayoutConfig.Cycler filters) {
        LinkedHashMap<String, String> options = GuiCyclers.icons(configs);
        String selectedIcon = selected(session, ATTR_FILTER_ICON, options);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (FilterConfig.HomeIconFilter filter : configs.filter().homeIconFilters().values()) {
            if (filter instanceof FilterConfig.HomeIconFilter.All) {
                counts.put(filter.id(), (long) configs.filter().homeIcons().size());
            } else {
                counts.put(filter.id(), (long) filter.matchIds().size());
            }
        }
        writeCycler(session, "filter_ICON", "icon", filters, options, selectedIcon, counts, LayoutConfig.Cycler.Format.FILTER_DEFAULT);
    }
}