package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.core.gui.action.ActionTokens;

import java.util.Map;
import java.util.logging.Logger;

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
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    // kit

    private void previewKit(ActionContext context, String args) {
        Map<String, String> tokens = ActionTokens.parse(args);
        String guiId = tokens.get("gui");
        String kitId = tokens.get("id");

        if (guiId == null || guiId.isBlank()) {
            logger.warning("Action '[preview] type:kit' is missing a required 'gui:' argument - it will not do "
                    + "anything until this is fixed");
            return;
        }
        if (kitId == null || kitId.isBlank()) {
            logger.warning("Action '[preview] type:kit gui:" + guiId + "' is missing a required 'id:' argument - "
                    + "it will not do anything until this is fixed");
            return;
        }
        if (!GuiActions.guiEnabled(guis, guiId)) {
            logger.warning("Action '[preview] type:kit gui:" + guiId + " id:" + kitId + "' points at gui '" + guiId
                    + "', which isn't a registered/enabled GUI - it will not do anything until this is fixed");
            return;
        }

        ScreenOpener.open(context, guiId, Map.of("id", kitId));
    }
}