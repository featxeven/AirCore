package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.render.RenderEntry;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.worth.WorthCalculator;
import com.ftxeven.aircore.module.economy.worth.WorthSort;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.item.HoldingKind;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class BaseWorthGui extends BaseHoldingGui {

    public static final String ATTR_FILTER_CATEGORY = "filter-category";
    public static final String ATTR_SORT_VALUE = "sort-value";
    public static final String ATTR_SEARCH_QUERY = "worth-search-query";

    protected final ConfigManager configs;
    protected final Supplier<EconomyModule> economy;

    protected BaseWorthGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis) {
        super(services, guis, HoldingKind.SELL);
        this.configs = configs;
        this.economy = economy;
    }

    protected abstract List<WorthCalculator.Entry> visibleEntries(GuiSession session, List<WorthCalculator.Entry> catalog);

    @Override
    public void prepare(Player viewer, GuiSession session) {
        session.flagResolver(GuiFlags.builder(viewer).build());

        LayoutConfig layout = layout(session);
        List<WorthCalculator.Entry> catalog = catalog(session, layout);
        List<WorthCalculator.Entry> searched = visibleEntries(session, catalog);
        PageResult<WorthCalculator.Entry> page = paginate(session, layout, withinCategory(session, layout.filters(), searched));

        Map<String, String> placeholders = session.placeholders();
        placeholders.put("current", String.valueOf(page.totalResults()));
        placeholders.put("total", String.valueOf(catalog.size()));
        GuiCyclers.write(session, configs, layout.filters(), GuiCyclers.WORTH_CATEGORY, categoryCounts(searched));
        GuiCyclers.write(session, configs, layout.sorts(), GuiCyclers.WORTH_SORT, null);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        retainHolding(viewer, session);

        LayoutConfig layout = layout(session);
        ItemConfig.Template base = layout.worth();
        if (base == null || layout.worthSlots().isEmpty()) {
            return;
        }

        List<WorthCalculator.Entry> searched = visibleEntries(session, catalog(session, layout));
        PageResult<WorthCalculator.Entry> page = paginate(session, layout, withinCategory(session, layout.filters(), searched));
        List<RenderEntry> entries = new ArrayList<>(page.items().size());
        for (WorthCalculator.Entry entry : page.items()) {
            Map<String, String> specific = new LinkedHashMap<>();
            GuiPlaceholders.formatWorth(specific, economy.get().formatter(), entry);
            entries.add(new RenderEntry(base.withMaterial(entry.key()), null, GuiPlaceholders.combine(session, specific),
                    GuiFlags.forWorth(viewer, entry), entry.key()));
        }
        guis.guis().renderer().drawEntries(viewer, session, entries, layout.worthSlots());
    }

    protected LayoutConfig layout(GuiSession session) {
        return guis.layouts().layout(session.definition());
    }

    // Filter selection -> entries

    protected Optional<FilterConfig.WorthCategory> resolveCategoryFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded(GuiCyclers.WORTH_CATEGORY.id())) {
            return Optional.empty();
        }
        String selectedId = GuiCyclers.selected(session, configs, GuiCyclers.WORTH_CATEGORY);
        if (selectedId.equals(FilterConfig.ALL_ID)) {
            return Optional.empty();
        }
        return configs.filter().worthCategory(selectedId);
    }

    protected List<WorthCalculator.Entry> withinCategory(GuiSession session, LayoutConfig.Cycler filters, List<WorthCalculator.Entry> entries) {
        return resolveCategoryFilter(session, filters)
                .map(category -> entries.stream().filter(entry -> inCategory(category, entry)).toList())
                .orElse(entries);
    }

    private static boolean inCategory(FilterConfig.WorthCategory category, WorthCalculator.Entry entry) {
        return category.matches(entry.key()) || entry.categories().contains(category.id());
    }

    private Map<String, Long> categoryCounts(List<WorthCalculator.Entry> searched) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FilterConfig.WorthCategory category : configs.filter().worthCategories().values()) {
            counts.put(category.id(), searched.stream().filter(entry -> inCategory(category, entry)).count());
        }
        return counts;
    }

    // Sorting

    protected WorthSort currentSort(GuiSession session, LayoutConfig.Cycler sorts) {
        if (sorts.isExcluded(GuiCyclers.WORTH_SORT.id())) {
            return WorthSort.DEFAULT;
        }
        return GuiCyclers.worthSort(GuiCyclers.selected(session, configs, GuiCyclers.WORTH_SORT));
    }

    // Catalog

    protected List<WorthCalculator.Entry> catalog(GuiSession session, LayoutConfig layout) {
        return economy.get().worth().catalog(currentSort(session, layout.sorts()));
    }

    // Pagination

    protected PageResult<WorthCalculator.Entry> paginate(GuiSession session, LayoutConfig layout, List<WorthCalculator.Entry> entries) {
        PageResult<WorthCalculator.Entry> page = PageResult.of(entries, session.page(), Math.max(1, layout.worthSlots().size()));
        session.page(page.page());
        session.totalPages(page.totalPages());
        return page;
    }
}