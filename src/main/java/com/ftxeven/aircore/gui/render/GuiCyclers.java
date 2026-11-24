package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.action.CyclerAction;
import com.ftxeven.aircore.database.query.HomeSort;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.BaseWorthGui;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.module.economy.worth.WorthSort;
import com.ftxeven.aircore.util.Placeholders;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class GuiCyclers {

    private GuiCyclers() {
    }

    // Dimensions

    public enum Kind {
        FILTER("filter", LayoutConfig.Cycler.Format.FILTER_DEFAULT),
        SORT("sort", LayoutConfig.Cycler.Format.SORT_DEFAULT);

        private final String key;
        private final LayoutConfig.Cycler.Format fallback;

        Kind(String key, LayoutConfig.Cycler.Format fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        // the leading part of this kind's placeholders and PlaceholderAPI keys: "filter_" / "sort_"
        public String keyPrefix() {
            return key + "_";
        }

        // the part of a layout that lists this kind's excluded dimensions and display formats
        private LayoutConfig.Cycler in(LayoutConfig layout) {
            return this == FILTER ? layout.filters() : layout.sorts();
        }
    }

    public record Dimension(String module, Kind kind, String id, String attribute,
                            Function<ConfigManager, LinkedHashMap<String, String>> source) {

        public LinkedHashMap<String, String> options(ConfigManager configs) {
            return source.apply(configs);
        }

        public boolean offeredBy(LayoutConfig layout) {
            return !kind.in(layout).isExcluded(id);
        }

        private String placeholder() {
            return kind.keyPrefix() + id.toUpperCase(Locale.ROOT);
        }
    }

    // Homes
    public static final Dimension HOME_WORLD = new Dimension("home", Kind.FILTER, "world", BaseHomeGui.ATTR_FILTER_WORLD, GuiCyclers::homeWorlds);
    public static final Dimension HOME_ICON = new Dimension("home", Kind.FILTER, "icon", BaseHomeGui.ATTR_FILTER_ICON, GuiCyclers::homeIcons);
    public static final Dimension HOME_SORT = new Dimension("home", Kind.SORT, "home", BaseHomeGui.ATTR_SORT_HOME, GuiCyclers::homeSorts);

    // Worth
    public static final Dimension WORTH_CATEGORY = new Dimension("worth", Kind.FILTER, "category", BaseWorthGui.ATTR_FILTER_CATEGORY, GuiCyclers::worthCategories);
    public static final Dimension WORTH_SORT = new Dimension("worth", Kind.SORT, "value", BaseWorthGui.ATTR_SORT_VALUE, GuiCyclers::worthSorts);

    private static final List<Dimension> DIMENSIONS = List.of(HOME_WORLD, HOME_ICON, HOME_SORT, WORTH_CATEGORY, WORTH_SORT);

    // Lookup

    public static @Nullable Dimension find(Kind kind, String id) {
        for (Dimension dimension : DIMENSIONS) {
            if (dimension.kind() == kind && dimension.id().equalsIgnoreCase(id)) {
                return dimension;
            }
        }
        return null;
    }

    private static @Nullable Dimension find(Kind kind, String module, String id) {
        for (Dimension dimension : DIMENSIONS) {
            if (dimension.kind() == kind && dimension.module().equals(module) && dimension.id().equals(id)) {
                return dimension;
            }
        }
        return null;
    }

    // Actions

    public static @Nullable CyclerAction.Dimensions dimensions(Kind kind, String module, ConfigManager configs,
                                                               Function<GuiSession, LayoutConfig> layouts) {
        boolean declared = DIMENSIONS.stream().anyMatch(dimension -> dimension.kind() == kind && dimension.module().equals(module));
        return declared ? new ActionView(kind, module, configs, layouts) : null;
    }

    private static final class ActionView implements CyclerAction.Dimensions {

        private final Kind kind;
        private final String module;
        private final ConfigManager configs;
        private final Function<GuiSession, LayoutConfig> layouts;

        private ActionView(Kind kind, String module, ConfigManager configs, Function<GuiSession, LayoutConfig> layouts) {
            this.kind = kind;
            this.module = module;
            this.configs = configs;
            this.layouts = layouts;
        }

        @Override
        public @Nullable String attribute(String dimension) {
            Dimension found = find(kind, module, dimension);
            return found != null ? found.attribute() : null;
        }

        @Override
        public @Nullable List<String> options(GuiSession session, String dimension) {
            Dimension found = find(kind, module, dimension);
            if (found == null || !found.offeredBy(layouts.apply(session))) {
                return null;
            }
            return List.copyOf(found.options(configs).keySet());
        }
    }

    // Selection / placeholders

    public static String selected(GuiSession session, ConfigManager configs, Dimension dimension) {
        return selected(session, dimension, dimension.options(configs));
    }

    private static String selected(GuiSession session, Dimension dimension, LinkedHashMap<String, String> options) {
        String value = session.attribute(dimension.attribute(), String.class);
        if (value != null && (value.equals(FilterConfig.ALL_ID) || options.containsKey(value))) {
            return value;
        }
        String resolved = options.isEmpty() ? FilterConfig.ALL_ID : options.keySet().iterator().next();
        session.attribute(dimension.attribute(), resolved);
        return resolved;
    }

    // Writes %<kind>_<ID>%, %<kind>_<ID>_id% and %<kind>_<ID>_list% for a dimension
    public static void write(GuiSession session, ConfigManager configs, LayoutConfig.Cycler cycler,
                             Dimension dimension, @Nullable Map<String, Long> counts) {
        LinkedHashMap<String, String> options = dimension.options(configs);
        String current = selected(session, dimension, options);
        if (options.isEmpty()) {
            return;
        }

        LayoutConfig.Cycler.Format format = cycler.format(dimension.id(), dimension.kind().fallback);
        String key = dimension.placeholder();

        Map<String, String> placeholders = session.placeholders();
        placeholders.put(key, options.getOrDefault(current, current));
        placeholders.put(key + "_id", current);

        StringBuilder list = new StringBuilder();
        for (Map.Entry<String, String> option : options.entrySet()) {
            String template = option.getKey().equals(current) ? format.selected() : format.unselected();
            long count = counts != null ? counts.getOrDefault(option.getKey(), 0L) : 0L;
            String line = Placeholders.apply(null, template, Map.of("name", option.getValue(), "count", String.valueOf(count)));
            if (!list.isEmpty()) {
                list.append('\n');
            }
            list.append(line);
        }
        placeholders.put(key + "_list", list.toString());
    }

    // Homes

    private static final Map<String, HomeSort> HOME_SORT_KEYS = Map.of(
            "newest", HomeSort.NEWEST,
            "oldest", HomeSort.OLDEST,
            "alphabetical", HomeSort.ALPHABETICAL,
            "favorite", HomeSort.FAVORITE
    );

    public static LinkedHashMap<String, String> homeWorlds(ConfigManager configs) {
        return new LinkedHashMap<>(configs.filter().homeWorlds());
    }

    public static LinkedHashMap<String, String> homeIcons(ConfigManager configs) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        configs.filter().homeIconFilters().forEach((id, filter) -> options.put(id, filter.label()));
        return options;
    }

    public static LinkedHashMap<String, String> homeSorts(ConfigManager configs) {
        return new LinkedHashMap<>(configs.homes().gui().sort());
    }

    public static HomeSort homeSort(String key) {
        return HOME_SORT_KEYS.getOrDefault(key, HomeSort.ALPHABETICAL);
    }

    // Filter-bucket resolution

    public static String worldFilterKey(FilterConfig filter, String world) {
        return filter.homeWorlds().containsKey(world) ? world : FilterConfig.ALL_ID;
    }

    public static String iconFilterKey(FilterConfig filter, @Nullable String icon) {
        if (icon == null) {
            return FilterConfig.ALL_ID;
        }
        return filter.homeIcon(icon)
                .map(resolved -> resolved.bundleId() != null ? resolved.bundleId() : resolved.id())
                .orElse(FilterConfig.ALL_ID);
    }

    public static boolean iconResolvable(FilterConfig filter, @Nullable String icon) {
        return icon != null && filter.homeIcon(icon).isPresent();
    }

    // Worth

    private static final Map<String, WorthSort> WORTH_SORT_KEYS = Map.of(
            "default", WorthSort.DEFAULT,
            "highest_value", WorthSort.HIGHEST_VALUE,
            "lowest_value", WorthSort.LOWEST_VALUE
    );

    public static LinkedHashMap<String, String> worthCategories(ConfigManager configs) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        configs.filter().worthCategories().forEach((id, category) -> options.put(id, category.label()));
        return options;
    }

    public static LinkedHashMap<String, String> worthSorts(ConfigManager configs) {
        return new LinkedHashMap<>(configs.economy().gui().sell().sort().worth());
    }

    public static WorthSort worthSort(String key) {
        return WORTH_SORT_KEYS.getOrDefault(key, WorthSort.DEFAULT);
    }
}