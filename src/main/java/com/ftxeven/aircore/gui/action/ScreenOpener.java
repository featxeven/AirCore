package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ForwardNavigation;
import com.ftxeven.aircore.core.gui.flag.FlagGate;
import com.ftxeven.aircore.core.gui.nav.GuiContext;
import com.ftxeven.aircore.core.gui.nav.ScreenKey;
import com.ftxeven.aircore.core.gui.nav.ScreenState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ScreenOpener {

    private ScreenOpener() {
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes) {
        open(context, guiId, attributes, Map.of(), FlagGate.NO_FLAGS);
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes,
                            Map<String, String> extraPlaceholders, Function<String, String> flagResolver) {
        GuiSession current = context.session();
        ScreenKey currentScreen = current.screenKey();
        ScreenKey forwardScreen = new ScreenKey(guiId, currentScreen.target());
        GuiContext backLink = new GuiContext(currentScreen, current.originChain(), current.flagResolver(), current.navBack());

        ScreenState currentLive = ScreenState.liveStateOf(current);
        ScreenState forward = ForwardNavigation.resolveForwardState(context, forwardScreen, currentLive, ForwardNavigation.parse("", context));

        Map<String, String> placeholders = new LinkedHashMap<>(current.placeholders());
        placeholders.putAll(extraPlaceholders);

        OpenOptions options = OpenOptions.forScreen(flagResolver, forwardScreen, forward, backLink, current.forwardChain(), attributes);
        context.manager().open(context.viewer(), guiId, placeholders, options);
    }
}