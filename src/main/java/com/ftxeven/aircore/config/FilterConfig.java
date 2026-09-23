package com.ftxeven.aircore.config;

import com.ftxeven.aircore.core.gui.config.AliasExpander;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.config.ItemConfigReader;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class FilterConfig extends BaseConfig {

    public static final String ALL_ID = "all";

    private final ItemConfigReader itemReader;
    private final AliasExpander expander;

    private volatile Map<String, String> homeWorlds;
    private volatile Map<String, HomeIconFilter> homeIconFilters;
    private volatile Map<String, HomeIcon> homeIcons;

    public FilterConfig(JavaPlugin plugin) {
        super(plugin, "data/filter.yml");
        this.itemReader = new ItemConfigReader(plugin.getLogger());
        this.expander = new AliasExpander(Map.of(), plugin.getLogger());
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        ConfigurationSection homesSec = orEmpty(yaml.getConfigurationSection("homes"));
        homeWorlds = readLabelMap(homesSec.getConfigurationSection("worlds"));

        Map<String, HomeIconFilter> filters = new LinkedHashMap<>();
        Map<String, HomeIcon> icons = new LinkedHashMap<>();
        readHomeIcons(homesSec.getConfigurationSection("icons"), filters, icons);
        homeIconFilters = Collections.unmodifiableMap(filters);
        homeIcons = Collections.unmodifiableMap(icons);
    }

    public Map<String, String> homeWorlds() {
        return homeWorlds;
    }

    public Map<String, HomeIconFilter> homeIconFilters() {
        return homeIconFilters;
    }

    public Map<String, HomeIcon> homeIcons() {
        return homeIcons;
    }

    public Optional<HomeIcon> homeIcon(String id) {
        return Optional.ofNullable(homeIcons.get(id));
    }

    public String homeIconLabel(@Nullable String iconId) {
        if (iconId == null) {
            return allIconLabel();
        }
        return homeIcon(iconId).map(HomeIcon::label).orElseGet(this::allIconLabel);
    }

    private String allIconLabel() {
        HomeIconFilter all = homeIconFilters.get(ALL_ID);
        return all != null ? all.label() : ALL_ID;
    }

    public String bundleLabel(String bundleId) {
        HomeIconFilter filter = homeIconFilters.get(bundleId);
        return filter != null ? filter.label() : bundleId;
    }

    // Section readers

    private void readHomeIcons(@Nullable ConfigurationSection sec, Map<String, HomeIconFilter> filters, Map<String, HomeIcon> icons) {
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = orEmpty(sec.getConfigurationSection(key));

            if (key.equals(ALL_ID)) {
                filters.put(key, new HomeIconFilter.All(entry.getString("label", "All")));
                continue;
            }

            ConfigurationSection bundleSec = entry.getConfigurationSection("bundle");
            if (bundleSec != null) {
                List<HomeIcon> members = new ArrayList<>();
                for (String iconKey : bundleSec.getKeys(false)) {
                    if (isReserved(iconKey, "bundle '" + key + "'") || isDuplicate(iconKey, icons, "bundle '" + key + "'")) {
                        continue;
                    }
                    HomeIcon icon = readHomeIcon(orEmpty(bundleSec.getConfigurationSection(iconKey)), iconKey, key);
                    icons.put(iconKey, icon);
                    members.add(icon);
                }
                filters.put(key, new HomeIconFilter.Bundle(key, entry.getString("label", key), List.copyOf(members)));
                continue;
            }

            if (isDuplicate(key, icons, null)) {
                continue;
            }
            HomeIcon icon = readHomeIcon(entry, key, null);
            icons.put(key, icon);
            filters.put(key, new HomeIconFilter.Standalone(key, icon.label(), icon));
        }
    }

    private boolean isReserved(String iconKey, String where) {
        if (!iconKey.equals(ALL_ID)) {
            return false;
        }
        plugin.getLogger().warning("Home icon '" + iconKey + "' in data/filter.yml (" + where + ") uses the reserved "
                + "id '" + ALL_ID + "', which isn't assignable - skipping it");
        return true;
    }

    private boolean isDuplicate(String iconKey, Map<String, HomeIcon> icons, @Nullable String where) {
        if (!icons.containsKey(iconKey)) {
            return false;
        }
        plugin.getLogger().warning("Home icon '" + iconKey + "' in data/filter.yml"
                + (where != null ? " (" + where + ")" : "") + " was already defined earlier - keeping the first definition");
        return true;
    }

    private HomeIcon readHomeIcon(ConfigurationSection sec, String id, @Nullable String bundleId) {
        return new HomeIcon(
                id,
                sec.getString("label", id),
                bundleId,
                readItemFields(sec.getConfigurationSection("home"), "home icon '" + id + "' home"),
                readItemFields(sec.getConfigurationSection("icon"), "home icon '" + id + "' icon")
        );
    }

    private @Nullable ItemConfig.Fields readItemFields(@Nullable ConfigurationSection sec, String context) {
        return sec != null ? itemReader.readFields(sec, expander, context) : null;
    }

    // Section types

    public sealed interface HomeIconFilter {
        String id();
        String label();
        Set<String> matchIds();

        record All(String label) implements HomeIconFilter {
            @Override public String id() { return ALL_ID; }
            @Override public Set<String> matchIds() { return Set.of(); }
        }

        record Standalone(String id, String label, HomeIcon icon) implements HomeIconFilter {
            @Override public Set<String> matchIds() { return Set.of(id); }
        }

        record Bundle(String id, String label, List<HomeIcon> icons) implements HomeIconFilter {
            @Override public Set<String> matchIds() {
                return icons.stream().map(HomeIcon::id).collect(Collectors.toUnmodifiableSet());
            }
        }
    }

    public record HomeIcon(String id, String label, @Nullable String bundleId,
                           @Nullable ItemConfig.Fields home, @Nullable ItemConfig.Fields icon) {
        public boolean hasBundle() {
            return bundleId != null;
        }

        public boolean isAccessibleTo(Permissible sender) {
            if (Permissions.Access.hasHomeIcon(sender, id)) {
                return true;
            }
            return bundleId != null && Permissions.Access.hasHomeBundle(sender, bundleId);
        }
    }
}