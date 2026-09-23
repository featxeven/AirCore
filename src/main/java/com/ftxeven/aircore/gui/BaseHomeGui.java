package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.core.gui.render.RenderEntry;
import com.ftxeven.aircore.database.query.HomeFacetCounts;
import com.ftxeven.aircore.database.query.HomeQuery;
import com.ftxeven.aircore.database.query.HomeSort;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Placeholders;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class BaseHomeGui implements GuiRenderer.DynamicRenderer {

    public static final String ATTR_TARGET_HOME = "target-home";

    public static final String ATTR_FILTER_WORLD = "filter-world";
    public static final String ATTR_FILTER_ICON = "filter-icon";
    public static final String ATTR_SORT_HOME = "sort-home";
    public static final String ATTR_SEARCH_QUERY = "home-search-query";

    protected final ConfigManager configs;
    protected final ServiceManager services;
    protected final Supplier<HomesModule> homes;
    protected final PluginGuiManager guis;

    protected BaseHomeGui(ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, PluginGuiManager guis) {
        this.configs = configs;
        this.services = services;
        this.homes = homes;
        this.guis = guis;
    }

    protected LayoutConfig layout(GuiSession session) {
        return guis.layouts().layout(session.definition());
    }

    protected UUID owner(Player viewer, GuiSession session) {
        return GuiPlaceholders.resolveOwner(session.target(), viewer);
    }

    protected void installFlags(Player viewer, GuiSession session, @Nullable Home home) {
        UUID owner = owner(viewer, session);
        session.flagResolver(GuiFlags.forHome(viewer, configs.filter(), owner, home, homes.get().homeResolver(owner)));
    }

    protected @Nullable Home resolveTargetHome(Player viewer, GuiSession session) {
        UUID owner = owner(viewer, session);
        String homeName = session.attribute("id", String.class);
        Home home = homeName != null ? homes.get().find(owner, homeName).orElse(null) : null;

        session.attribute(ATTR_TARGET_HOME, home);
        installFlags(viewer, session, home);
        session.placeholders().putAll(home != null
                ? homePlaceholders(home)
                : GuiPlaceholders.homeIcon(configs.filter(), null));
        return home;
    }

    // Draws the home resolved by resolveTargetHome() into this screen's home-slots
    protected void drawTargetHome(Player viewer, GuiSession session) {
        LayoutConfig layout = layout(session);
        ItemConfig.Template homeTemplate = layout.home();
        Home home = session.attribute(ATTR_TARGET_HOME, Home.class);
        if (homeTemplate == null || home == null) {
            return;
        }
        UUID owner = owner(viewer, session);
        ItemConfig.Fields override = home.icon() != null
                ? configs.filter().homeIcon(home.icon()).map(FilterConfig.HomeIcon::home).orElse(null)
                : null;
        Map<String, String> placeholders = entryPlaceholders(session, Map.of());
        RenderEntry entry = new RenderEntry(overlayIcon(homeTemplate, override), null, placeholders,
                GuiFlags.forHome(viewer, configs.filter(), owner, home, homes.get().homeResolver(owner)));
        drawEntries(viewer, session, List.of(entry), layout.homeSlots());
    }

    // Filter option sources

    protected LinkedHashMap<String, String> worldOptions() {
        return GuiCyclers.worlds(configs);
    }

    protected LinkedHashMap<String, String> iconOptions() {
        return GuiCyclers.icons(configs);
    }

    protected LinkedHashMap<String, String> sortOptions() {
        return GuiCyclers.sorts(configs);
    }

    // Filter / sort attribute selection

    protected String selected(GuiSession session, String attributeKey, LinkedHashMap<String, String> options) {
        String value = session.attribute(attributeKey, String.class);
        if (value != null && (value.equals(FilterConfig.ALL_ID) || options.containsKey(value))) {
            return value;
        }
        String resolved = options.isEmpty() ? FilterConfig.ALL_ID : options.keySet().iterator().next();
        session.attribute(attributeKey, resolved);
        return resolved;
    }

    // Filter selection -> query value

    protected Optional<String> resolveWorldFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded("world")) {
            return Optional.empty();
        }
        String world = selected(session, ATTR_FILTER_WORLD, worldOptions());
        return world.equals(FilterConfig.ALL_ID) ? Optional.empty() : Optional.of(world);
    }

    protected Optional<Set<String>> resolveIconFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded("icon")) {
            return Optional.empty();
        }
        String selectedId = selected(session, ATTR_FILTER_ICON, iconOptions());
        if (selectedId.equals(FilterConfig.ALL_ID)) {
            return Optional.empty();
        }
        return Optional.ofNullable(configs.filter().homeIconFilters().get(selectedId))
                .map(FilterConfig.HomeIconFilter::matchIds);
    }

    // Sorting

    protected HomeSort currentSort(GuiSession session, LayoutConfig.Cycler sorts) {
        if (sorts.isExcluded("home")) {
            return HomeSort.ALPHABETICAL;
        }
        String key = selected(session, ATTR_SORT_HOME, sortOptions());
        return GuiCyclers.resolveSort(key);
    }

    // Query building

    protected HomeQuery.Builder homeQueryBuilder(GuiSession session, UUID owner, int pageSize, int page) {
        LayoutConfig.Cycler filters = layout(session).filters();
        HomeQuery.Builder builder = HomeQuery.builder(owner, pageSize)
                .page(page)
                .sort(currentSort(session, layout(session).sorts()));

        resolveWorldFilter(session, filters).ifPresent(builder::world);
        resolveIconFilter(session, filters).ifPresent(builder::icons);
        return builder;
    }

    // Pagination + info placeholders

    protected void writePageResult(GuiSession session, PageResult<Home> page, int totalOwned) {
        Map<String, String> placeholders = session.placeholders();
        placeholders.put("current", String.valueOf(page.totalResults()));
        placeholders.put("total", String.valueOf(totalOwned));
        session.totalPages(page.totalPages());
    }

    // Cycler placeholders

    protected void writeFilterCyclers(GuiSession session, LayoutConfig.Cycler filters, Supplier<HomeFacetCounts> facets) {
        HomeFacetCounts counts = facets.get();

        LinkedHashMap<String, String> worldOptions = worldOptions();
        String selectedWorld = selected(session, ATTR_FILTER_WORLD, worldOptions);
        writeCycler(session, "filter_WORLD", "world", filters, worldOptions, selectedWorld, worldFilterCounts(counts.byWorld()), LayoutConfig.Cycler.Format.FILTER_DEFAULT);

        LinkedHashMap<String, String> iconOptions = iconOptions();
        String selectedIcon = selected(session, ATTR_FILTER_ICON, iconOptions);
        writeCycler(session, "filter_ICON", "icon", filters, iconOptions, selectedIcon, iconFilterCounts(counts.byIcon()), LayoutConfig.Cycler.Format.FILTER_DEFAULT);
    }

    // buckets raw per-world counts under the filter entry each world belongs to, folding any
    // world that isn't (or is no longer) registered in filter.yml into "all"
    private Map<String, Long> worldFilterCounts(Map<String, Long> byWorld) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;
        for (Map.Entry<String, Long> entry : byWorld.entrySet()) {
            if (entry.getKey().equals(FilterConfig.ALL_ID)) {
                all = entry.getValue();
                continue;
            }
            counts.merge(GuiCyclers.worldFilterKey(configs.filter(), entry.getKey()), entry.getValue(), Long::sum);
        }
        counts.put(FilterConfig.ALL_ID, all);
        return counts;
    }

    // same idea for icons: bucketed under the icon itself, or its bundle if it belongs to one,
    // folding a home with no icon
    private Map<String, Long> iconFilterCounts(Map<String, Long> byIcon) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;
        for (Map.Entry<String, Long> entry : byIcon.entrySet()) {
            if (entry.getKey().equals(FilterConfig.ALL_ID)) {
                all = entry.getValue();
                continue;
            }
            counts.merge(GuiCyclers.iconFilterKey(configs.filter(), entry.getKey()), entry.getValue(), Long::sum);
        }
        counts.put(FilterConfig.ALL_ID, all);
        return counts;
    }

    protected void writeSortCycler(GuiSession session, LayoutConfig.Cycler sorts) {
        LinkedHashMap<String, String> options = sortOptions();
        String selectedSort = selected(session, ATTR_SORT_HOME, options);
        writeCycler(session, "sort_HOME", "home", sorts, options, selectedSort, null, LayoutConfig.Cycler.Format.SORT_DEFAULT);
    }

    protected void writeCycler(GuiSession session, String placeholderKey, String dimension, LayoutConfig.Cycler cycler,
                               LinkedHashMap<String, String> options, String selectedOption, @Nullable Map<String, Long> counts,
                               LayoutConfig.Cycler.Format fallback) {
        if (options.isEmpty()) {
            return;
        }

        LayoutConfig.Cycler.Format format = cycler.format(dimension, fallback);

        Map<String, String> placeholders = session.placeholders();
        placeholders.put(placeholderKey, options.getOrDefault(selectedOption, selectedOption));
        placeholders.put(placeholderKey + "_id", selectedOption);

        StringBuilder list = new StringBuilder();
        for (Map.Entry<String, String> option : options.entrySet()) {
            String template = option.getKey().equals(selectedOption) ? format.selected() : format.unselected();
            long count = counts != null ? counts.getOrDefault(option.getKey(), 0L) : 0L;
            String line = Placeholders.apply(null, template, Map.of("name", option.getValue(), "count", String.valueOf(count)));
            if (!list.isEmpty()) {
                list.append('\n');
            }
            list.append(line);
        }
        placeholders.put(placeholderKey + "_list", list.toString());
    }

    // Query clamping

    protected ClampedPage queryClamped(int requestedPage, IntFunction<PageResult<Home>> query) {
        int page = Math.max(1, requestedPage);
        PageResult<Home> result = query.apply(page);

        int totalPages = Math.max(1, result.totalPages());
        if (page > totalPages) {
            page = totalPages;
            result = query.apply(page);
        }
        return new ClampedPage(result, page);
    }

    protected record ClampedPage(PageResult<Home> result, int page) {}

    // Entry drawing

    protected Iterator<Integer> drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Iterator<Integer> slots) {
        return guis.guis().renderer().drawEntries(viewer, session, entries, slots);
    }

    protected void drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Set<Integer> slots) {
        guis.guis().renderer().drawEntries(viewer, session, entries, slots);
    }

    // Placeholders

    protected Map<String, String> entryPlaceholders(GuiSession session, Map<String, String> specific) {
        return GuiPlaceholders.combine(session, specific);
    }

    protected Map<String, String> homePlaceholders(Home home) {
        return GuiPlaceholders.homeWithIcon(configs, home);
    }

    protected List<RenderEntry> homeEntries(Player viewer, GuiSession session, UUID owner, List<Home> pageItems) {
        ItemConfig.Template base = layout(session).home();
        if (base == null) {
            return List.of();
        }
        List<RenderEntry> entries = new ArrayList<>(pageItems.size());
        for (Home home : pageItems) {
            ItemConfig.Fields override = home.icon() != null
                    ? configs.filter().homeIcon(home.icon()).map(FilterConfig.HomeIcon::home).orElse(null)
                    : null;
            Map<String, String> placeholders = entryPlaceholders(session, homePlaceholders(home));
            entries.add(new RenderEntry(overlayIcon(base, override), null, placeholders,
                    GuiFlags.forHome(viewer, configs.filter(), owner, home, homes.get().homeResolver(owner)), home.name()));
        }
        return entries;
    }

    protected void drawAvailableSlots(Player viewer, GuiSession session, UUID owner, int currentCount, Iterator<Integer> remainingSlots) {
        LayoutConfig.AvailableSlots available = layout(session).availableSlots();
        if (!available.enabled() || !remainingSlots.hasNext()) {
            return;
        }

        int limit = limitForOwner(owner);
        int remaining = limit < 0 ? Integer.MAX_VALUE : Math.max(0, limit - currentCount);
        if (remaining == 0) {
            return;
        }

        RenderEntry tile = new RenderEntry(available.template(), null, session.placeholders(), session.flagResolver());
        drawEntries(viewer, session, Collections.nCopies(remaining, tile), remainingSlots);
    }

    private int limitForOwner(UUID owner) {
        Player online = org.bukkit.Bukkit.getPlayer(owner);
        return online != null ? homes.get().limitFor(online) : configs.homes().general().maxHomes();
    }

    // Icon overlay

    protected static ItemConfig.Template overlayIcon(ItemConfig.Template base, @Nullable ItemConfig.Fields override) {
        return override != null ? new ItemConfig.Template(base.fields().overlay(override), base.priority()) : base;
    }
}