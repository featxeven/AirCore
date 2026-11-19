package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.CyclerAction;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * [sort] type:home by:home to:next|previous|first|last|N
 * [sort] type:worth by:value to:next|previous|first|last|N
 */
public final class SortAction extends CyclerAction {

    private static final String VERB = "sort";

    private final ConfigManager configs;
    private final PluginGuiManager guis;

    public SortAction(ConfigManager configs, PluginGuiManager guis) {
        super(VERB);
        this.configs = configs;
        this.guis = guis;
    }

    @Override
    protected @Nullable Dimensions dimensionsFor(ActionContext context, Map<String, String> args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return null;
        }
        Dimensions dimensions = GuiCyclers.dimensions(GuiCyclers.Kind.SORT, type, configs,
                session -> guis.layouts().layout(session.definition()));
        if (dimensions == null) {
            GuiActions.warnUnknownType(context, VERB, type);
        }
        return dimensions;
    }
}