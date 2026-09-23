package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.util.Placeholders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public interface ConfirmableAction extends ActionRegistry.Handler {

    Decision decide(ActionContext context, Map<String, String> args);

    default ActionRegistry.Handler dispatcher(ConfigManager configs) {
        return (context, args) -> {
            String guiId = ActionTokens.parse(args).get("gui");
            if (guiId == null || guiId.isBlank()) {
                execute(context, args);
                return;
            }
            if (!GuiActions.guiEnabled(context.manager(), guiId)) {
                context.logger().warning("An action on item '" + context.itemKey() + "' in GUI '" + context.guiId()
                        + "' points its 'gui:' confirmation at '" + guiId
                        + "', which isn't a registered/enabled GUI - it will not do anything until this is fixed");
                return;
            }

            Map<String, String> tokens = resolvedTokens(context, args);
            tokens.remove("gui");

            switch (decide(context, tokens)) {
                case Decision.Denied(var denial) -> denial.send(context.viewer(), configs, context.messenger());
                case Decision.Immediate ignored -> execute(context, args);
                case Decision.NeedsConfirmation(var placeholders, var flags) ->
                        ScreenOpener.open(context, guiId, new LinkedHashMap<>(tokens), placeholders, flags);
            }
        };
    }

    private static Map<String, String> resolvedTokens(ActionContext context, String args) {
        Map<String, String> resolved = new LinkedHashMap<>();
        ActionTokens.parse(args).forEach((key, value) ->
                resolved.put(key, Placeholders.apply(context.viewer(), value, context.placeholders())));
        return resolved;
    }

    sealed interface Decision {
        record Denied(Eligibility.Denied denial) implements Decision {}
        record Immediate() implements Decision {}
        record NeedsConfirmation(Map<String, String> placeholders, Function<String, String> flags) implements Decision {}

        static Decision denied(Eligibility.Denied denial) { return new Denied(denial); }
        static Decision immediate() { return new Immediate(); }
        static Decision needsConfirmation(Map<String, String> placeholders, Function<String, String> flags) { return new NeedsConfirmation(placeholders, flags); }
    }
}