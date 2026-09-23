package com.ftxeven.aircore.gui.config;

import com.ftxeven.aircore.core.gui.config.AliasExpander;
import com.ftxeven.aircore.core.gui.config.ItemConfig;
import com.ftxeven.aircore.core.gui.config.ItemConfigReader;
import com.ftxeven.aircore.core.gui.config.SharedConfig;
import com.ftxeven.aircore.core.gui.config.SlotParser;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class LayoutConfigReader {

    private final Logger logger;
    private final ItemConfigReader itemReader;

    public LayoutConfigReader(Logger logger) {
        this.logger = logger;
        this.itemReader = new ItemConfigReader(logger);
    }

    public LayoutConfig read(@Nullable ConfigurationSection sec, SharedConfig shared, AliasExpander expander, int rows, String context) {
        if (sec == null) {
            return LayoutConfig.EMPTY;
        }
        int inventorySize = rows * 9;

        return new LayoutConfig(
                readCycler(sec.getConfigurationSection("filters"), expander, context + " filters"),
                readCycler(sec.getConfigurationSection("sorts"), expander, context + " sorts"),
                slots(sec, "item-slots", inventorySize, context),
                slots(sec, "storage-slots", inventorySize, context),
                slots(sec, "hotbar-slots", inventorySize, context),
                slots(sec, "armor-slots", inventorySize, context),
                slots(sec, "offhand-slots", inventorySize, context),
                slots(sec, "enderchest-slots", inventorySize, context),
                slots(sec, "shulker-slots", inventorySize, context),
                slots(sec, "preview-slots", inventorySize, context),
                slots(sec, "home-slots", inventorySize, context),
                slots(sec, "icon-slots", inventorySize, context),
                slots(sec, "entry-slots", inventorySize, context),
                slots(sec, "disposal-slots", inventorySize, context),
                slots(sec, "sell-slots", inventorySize, context),
                template(sec, "home", shared, expander, context),
                template(sec, "icon", shared, expander, context),
                template(sec, "entry", shared, expander, context),
                sec.isSet("leaderboard") ? expander.expand(sec.getString("leaderboard", ""), context + " leaderboard") : null,
                readAvailableSlots(sec.getConfigurationSection("available-slots"), shared, expander, context + " available-slots")
        );
    }

    private @Nullable Set<Integer> slots(ConfigurationSection sec, String key, int inventorySize, String context) {
        return sec.isSet(key) ? SlotParser.parse(sec.get(key), inventorySize, context + " " + key, logger) : null;
    }

    private @Nullable ItemConfig.Template template(ConfigurationSection sec, String key, SharedConfig shared, AliasExpander expander, String context) {
        ConfigurationSection entry = sec.getConfigurationSection(key);
        return entry != null ? itemReader.readTemplateRef(entry, shared, expander, context + " " + key) : null;
    }

    private @Nullable LayoutConfig.Cycler readCycler(@Nullable ConfigurationSection sec, AliasExpander expander, String context) {
        if (sec == null) {
            return null;
        }

        Map<String, LayoutConfig.Cycler.Format> format = new LinkedHashMap<>();
        ConfigurationSection formatSec = sec.getConfigurationSection("format");
        if (formatSec != null) {
            for (String key : formatSec.getKeys(false)) {
                ConfigurationSection entry = formatSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                format.put(key, new LayoutConfig.Cycler.Format(
                        expander.expand(entry.getString("selected", ""), context + " format." + key + " selected"),
                        expander.expand(entry.getString("unselected", ""), context + " format." + key + " unselected")
                ));
            }
        }

        return new LayoutConfig.Cycler(sec.getStringList("excluded"), format);
    }

    private @Nullable LayoutConfig.AvailableSlots readAvailableSlots(@Nullable ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        if (sec == null) {
            return null;
        }
        return new LayoutConfig.AvailableSlots(sec.getBoolean("enabled", false), itemReader.readTemplateRef(sec, shared, expander, context));
    }
}