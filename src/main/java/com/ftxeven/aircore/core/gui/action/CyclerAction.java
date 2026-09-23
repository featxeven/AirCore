package com.ftxeven.aircore.core.gui.action;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.nav.GuiContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class CyclerAction implements ActionRegistry.Handler {

    private final String name;

    protected CyclerAction(String name) {
        this.name = name;
    }

    @Override
    public final void execute(ActionContext context, String args) {
        Map<String, String> parsed = ActionTokens.parse(args);

        Dimensions dimensions = dimensionsFor(context, parsed);
        if (dimensions == null) {
            return; // missing or unknown 'type:' - already warned
        }
        String verb = "[" + name + "] type:" + parsed.get("type");

        String by = parsed.get("by");
        String to = parsed.get("to");
        if (by == null || to == null) {
            context.logger().warning(verb + " action on item '" + context.itemKey() + "' in GUI '" + context.guiId()
                    + "' requires both 'by:' and 'to:' (got '" + args + "') - it will not do anything until this is fixed");
            return;
        }

        String dimension = by.toLowerCase(Locale.ROOT);
        String attribute = dimensions.attribute(dimension);
        if (attribute == null) {
            context.logger().warning(verb + " action on item '" + context.itemKey() + "' in GUI '" + context.guiId()
                    + "' has unknown dimension 'by:" + by + "' - it will not do anything until this is fixed");
            return;
        }

        GuiSession session = context.session();
        List<String> keys = dimensions.options(session, dimension);
        if (keys == null || keys.isEmpty()) {
            return; // this screen doesn't offer that dimension right now
        }

        String current = session.attribute(attribute, String.class);
        String next = Cycle.resolve(keys, current, to, context.logger(), verb + " action (by:" + dimension + ")");
        if (next == null) {
            return;
        }

        session.attribute(attribute, next);

        GuiContext back = session.navBack();
        if (back != null) {
            context.manager().lockContextFields(context.viewer().getUniqueId(), back.screen(), null, Map.of(attribute, next));
        }

        context.manager().refresh(context.viewer());
    }

    // resolves 'type:' to the dimensions that type offers
    protected abstract @Nullable Dimensions dimensionsFor(ActionContext context, Map<String, String> args);

    public interface Dimensions {

        // the session attribute a dimension writes into
        @Nullable String attribute(String dimension);

        // ordered option keys
        @Nullable List<String> options(GuiSession session, String dimension);
    }
}