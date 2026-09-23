package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.CyclerAction;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * [filter] type:home by:world|icon to:next|previous|first|last|N
 */
public final class FilterAction extends CyclerAction {

    private static final String VERB = "filter";

    private final Dimensions homes;

    public FilterAction(ConfigManager configs, PluginGuiManager guis) {
        super(VERB);
        this.homes = new HomeFilters(configs, guis);
    }

    @Override
    protected @Nullable Dimensions dimensionsFor(ActionContext context, Map<String, String> args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "home" -> homes;
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield null;
            }
        };
    }

    // home

    private static final class HomeFilters implements Dimensions {

        private final ConfigManager configs;
        private final PluginGuiManager guis;

        private HomeFilters(ConfigManager configs, PluginGuiManager guis) {
            this.configs = configs;
            this.guis = guis;
        }

        @Override
        public @Nullable String attribute(String dimension) {
            return GuiCyclers.filterAttribute(dimension);
        }

        @Override
        public @Nullable List<String> options(GuiSession session, String dimension) {
            LayoutConfig.Cycler filters = guis.layouts().layout(session.definition()).filters();
            if (filters.isExcluded(dimension)) {
                return null; // this GUI's layout doesn't offer this filter dimension
            }
            Map<String, String> options = GuiCyclers.filterOptions(configs, dimension);
            return options != null ? List.copyOf(options.keySet()) : null;
        }
    }
}