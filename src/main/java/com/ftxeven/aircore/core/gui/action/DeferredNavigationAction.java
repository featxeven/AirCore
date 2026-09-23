package com.ftxeven.aircore.core.gui.action;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.nav.GuiContext;
import com.ftxeven.aircore.core.gui.nav.ScreenKey;
import com.ftxeven.aircore.core.gui.nav.ScreenState;
import com.ftxeven.aircore.util.Placeholders;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared shape behind any action that prompts the player for a value via the configured input
 * type (chat/dialog/sign) and only navigates once they answer. 'type:' resolves which
 * Destination (screen, input prompt, resulting attributes) the answer lands on. The action
 * resolves any 'gui:' override itself, so destination.guiId() is always the final screen
 */
public abstract class DeferredNavigationAction implements ActionRegistry.Handler {

    protected abstract @Nullable Destination destinationFor(ActionContext context, Map<String, String> args);

    protected @Nullable String directValueKey() {
        return null;
    }

    @Override
    public final void execute(ActionContext context, String args) {
        Destination destination = destinationFor(context, ActionTokens.parse(args));
        if (destination == null) {
            return;
        }

        ForwardNavigation.Parsed parsed = ForwardNavigation.parse(args, context, directValueKey());

        GuiSession current = context.session();
        ScreenState currentLive = ScreenState.liveStateOf(current);
        ForwardNavigation.applyRestore(context, current, currentLive, parsed);

        ScreenKey currentScreen = current.screenKey();
        ScreenKey forwardScreen = ForwardNavigation.resolveForwardScreen(context, destination.guiId(), currentScreen, parsed);
        ScreenState forward = ForwardNavigation.resolveForwardState(context, forwardScreen, currentLive, parsed);

        // re-triggering from the destination itself
        String sourceId = current.definition().id();
        boolean refining = sourceId.equals(destination.guiId());
        List<String> ancestorChain = refining ? current.originChain() : current.forwardChain(sourceId);
        GuiContext backLink = refining ? current.navBack() : new GuiContext(currentScreen, current.originChain(), current.flagResolver(), current.navBack());

        if (parsed.value() != null) {
            String answer = Placeholders.apply(context.viewer(), parsed.value(), context.placeholders());
            navigate(context, current, forwardScreen, forward, backLink, ancestorChain, destination, answer);
            return;
        }

        context.manager().input().request(context, destination.inputKey(), answer ->
                navigate(context, current, forwardScreen, forward, backLink, ancestorChain, destination, answer));
    }

    private void navigate(ActionContext context, GuiSession current, ScreenKey forwardScreen, ScreenState forward,
                          GuiContext backLink, List<String> ancestorChain, Destination destination, String answer) {
        context.manager().open(context.viewer(), forwardScreen.guiId(), new LinkedHashMap<>(current.placeholders()),
                OpenOptions.forScreen(context.flagResolver(), forwardScreen, forward, backLink, ancestorChain, destination.attributesFor(answer)));
    }

    /**
     * what a resolved 'type:' lands on: the destination screen, which input context prompts for
     * the value, and the session attributes the answer is written into on arrival
     */
    public interface Destination {

        String guiId();

        String inputKey();

        Map<String, Object> attributesFor(String answer);
    }
}