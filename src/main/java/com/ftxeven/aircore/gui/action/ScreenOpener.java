package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ForwardNavigation;
import com.ftxeven.aircore.core.gui.flag.FlagGate;
import com.ftxeven.aircore.core.gui.nav.GuiContext;
import com.ftxeven.aircore.core.gui.nav.ScreenKey;
import com.ftxeven.aircore.core.gui.nav.ScreenState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ScreenOpener {

    private ScreenOpener() {
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes) {
        open(context, guiId, attributes, null);
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes, @Nullable String page) {
        open(context, guiId, attributes, page, Map.of(), FlagGate.NO_FLAGS);
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes,
                            Map<String, String> extraPlaceholders, Function<String, String> flagResolver) {
        open(context, guiId, attributes, null, extraPlaceholders, flagResolver);
    }

    public static void open(ActionContext context, String guiId, Map<String, Object> attributes, @Nullable String page,
                            Map<String, String> extraPlaceholders, Function<String, String> flagResolver) {
        GuiSession current = context.session();
        ScreenKey currentScreen = current.screenKey();
        ScreenKey forwardScreen = new ScreenKey(guiId, currentScreen.target());
        GuiContext backLink = new GuiContext(currentScreen, current.originChain(), current.flagResolver(), current.navBack());

        ScreenState currentLive = ScreenState.liveStateOf(current);
        ForwardNavigation.Parsed navigation = ForwardNavigation.parse(page != null ? "page:" + page : "", context);
        ScreenState forward = ForwardNavigation.resolveForwardState(context, forwardScreen, currentLive, navigation);

        Map<String, String> placeholders = new LinkedHashMap<>(current.placeholders());
        placeholders.putAll(extraPlaceholders);

        OpenOptions options = OpenOptions.forScreen(flagResolver, forwardScreen, forward, backLink, current.forwardChain(), attributes);
        context.manager().open(context.viewer(), guiId, placeholders, options);
    }
}