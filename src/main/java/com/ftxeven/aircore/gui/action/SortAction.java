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
 * [sort] type:home by:home to:next|previous|first|last|N
 */
public final class SortAction extends CyclerAction {

    private static final String VERB = "sort";

    private final Dimensions homes;

    public SortAction(ConfigManager configs, PluginGuiManager guis) {
        super(VERB);
        this.homes = new HomeSorts(configs, guis);
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

    private static final class HomeSorts implements Dimensions {

        private final ConfigManager configs;
        private final PluginGuiManager guis;

        private HomeSorts(ConfigManager configs, PluginGuiManager guis) {
            this.configs = configs;
            this.guis = guis;
        }

        @Override
        public @Nullable String attribute(String dimension) {
            return GuiCyclers.sortAttribute(dimension);
        }

        @Override
        public @Nullable List<String> options(GuiSession session, String dimension) {
            LayoutConfig.Cycler sorts = guis.layouts().layout(session.definition()).sorts();
            if (sorts.isExcluded(dimension)) {
                return null; // this GUI's layout doesn't offer this sort dimension
            }
            Map<String, String> options = GuiCyclers.sortOptions(configs, dimension);
            return options != null ? List.copyOf(options.keySet()) : null;
        }
    }
}