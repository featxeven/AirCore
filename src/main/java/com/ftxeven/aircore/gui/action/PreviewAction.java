package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.logging.Logger;

/**
 * [preview] type:kit gui:<id> id:<kit> page:<first|next|previous|N>
 * [preview] type:worth gui:<id> page:<first|next|previous|N>
 * 'page:' is optional on both
 */
public final class PreviewAction implements ActionRegistry.Handler {

    private static final String VERB = "preview";

    private final GuiManager guis;
    private final Logger logger;

    public PreviewAction(GuiManager guis, Logger logger) {
        this.guis = guis;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "kit" -> previewKit(context, args);
            case "worth" -> previewWorth(context, args);
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    // kit

    private void previewKit(ActionContext context, String args) {
        Map<String, String> tokens = ActionTokens.parse(args);
        String guiId = requireGui("kit", tokens);
        if (guiId == null) {
            return;
        }

        String kitId = tokens.get("id");
        if (kitId == null || kitId.isBlank()) {
            logger.warning("Action '[preview] type:kit gui:" + guiId + "' is missing a required 'id:' argument - "
                    + "it will not do anything until this is fixed");
            return;
        }

        ScreenOpener.open(context, guiId, Map.of("id", kitId), tokens.get("page"));
    }

    // worth

    private void previewWorth(ActionContext context, String args) {
        Map<String, String> tokens = ActionTokens.parse(args);
        String guiId = requireGui("worth", tokens);
        if (guiId != null) {
            ScreenOpener.open(context, guiId, Map.of(), tokens.get("page"));
        }
    }

    // shared

    private @Nullable String requireGui(String type, Map<String, String> tokens) {
        String guiId = tokens.get("gui");
        if (guiId == null || guiId.isBlank()) {
            logger.warning("Action '[preview] type:" + type + "' is missing a required 'gui:' argument - it will not "
                    + "do anything until this is fixed");
            return null;
        }
        if (!GuiActions.guiEnabled(guis, guiId)) {
            logger.warning("Action '[preview] type:" + type + " gui:" + guiId + "' points at gui '" + guiId
                    + "', which isn't a registered/enabled GUI - it will not do anything until this is fixed");
            return null;
        }
        return guiId;
    }
}