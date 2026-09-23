package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.config.AliasExpander;
import com.ftxeven.aircore.core.gui.config.GuiConfig;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.config.LayoutConfigReader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class LayoutGuiRegistry {

    private final Logger logger;
    private final GuiManager guis;
    private final LayoutConfigReader reader;
    private final Map<ConfigurationSection, LayoutConfig> cache = new ConcurrentHashMap<>();

    public LayoutGuiRegistry(JavaPlugin plugin, GuiManager guis) {
        this.logger = plugin.getLogger();
        this.guis = guis;
        this.reader = new LayoutConfigReader(logger);
    }

    public void load() {
        AliasExpander expander = new AliasExpander(guis.shared().aliases(), logger);
        for (String id : guis.ids()) {
            guis.definition(id).ifPresent(config -> {
                cacheSection(config.layout(), config.id(), config.settings().rows(), expander);
                for (GuiConfig.ContextOverride override : config.contexts().values()) {
                    cacheSection(override.layout(), config.id(), override.settings().rows(), expander);
                }
            });
        }
    }

    public void reload() {
        cache.clear();
        load();
    }

    public LayoutConfig layout(GuiConfig config) {
        ConfigurationSection section = config.layout();
        if (section == null) {
            return LayoutConfig.EMPTY;
        }
        return cache.computeIfAbsent(section, s ->
                reader.read(s, guis.shared(), new AliasExpander(guis.shared().aliases(), logger), config.settings().rows(), "GUI '" + config.id() + "' layout"));
    }

    private void cacheSection(@Nullable ConfigurationSection section, String guiId, int rows, AliasExpander expander) {
        if (section != null) {
            cache.computeIfAbsent(section, s -> reader.read(s, guis.shared(), expander, rows, "GUI '" + guiId + "' layout"));
        }
    }
}