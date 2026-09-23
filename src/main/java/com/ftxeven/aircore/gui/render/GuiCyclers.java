package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.database.query.HomeSort;
import com.ftxeven.aircore.gui.BaseHomeGui;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GuiCyclers {

    public static final String WORLD = "world";
    public static final String ICON = "icon";
    public static final String HOME = "home";

    private static final Map<String, HomeSort> SORT_KEYS = Map.of(
            "newest", HomeSort.NEWEST,
            "oldest", HomeSort.OLDEST,
            "alphabetical", HomeSort.ALPHABETICAL,
            "favorite", HomeSort.FAVORITE
    );

    private GuiCyclers() {
    }

    public static LinkedHashMap<String, String> worlds(ConfigManager configs) {
        return new LinkedHashMap<>(configs.filter().homeWorlds());
    }

    public static LinkedHashMap<String, String> icons(ConfigManager configs) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        configs.filter().homeIconFilters().forEach((id, filter) -> options.put(id, filter.label()));
        return options;
    }

    public static LinkedHashMap<String, String> sorts(ConfigManager configs) {
        return new LinkedHashMap<>(configs.homes().gui().sort());
    }

    public static HomeSort resolveSort(String key) {
        return SORT_KEYS.getOrDefault(key, HomeSort.ALPHABETICAL);
    }

    // Dimension -> session attribute

    public static @Nullable String filterAttribute(String dimension) {
        return switch (dimension) {
            case WORLD -> BaseHomeGui.ATTR_FILTER_WORLD;
            case ICON -> BaseHomeGui.ATTR_FILTER_ICON;
            default -> null;
        };
    }

    public static @Nullable String sortAttribute(String dimension) {
        return HOME.equals(dimension) ? BaseHomeGui.ATTR_SORT_HOME : null;
    }

    // Dimension -> ordered options

    public static @Nullable LinkedHashMap<String, String> filterOptions(ConfigManager configs, String dimension) {
        return switch (dimension) {
            case WORLD -> worlds(configs);
            case ICON -> icons(configs);
            default -> null;
        };
    }

    public static @Nullable LinkedHashMap<String, String> sortOptions(ConfigManager configs, String dimension) {
        return HOME.equals(dimension) ? sorts(configs) : null;
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
}