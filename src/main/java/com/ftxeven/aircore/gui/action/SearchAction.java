package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.DeferredNavigationAction;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.BaseLeaderboardGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.impl.HomeSearchGui;
import com.ftxeven.aircore.gui.impl.LeaderboardSearchGui;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * [search] type:home|leaderboard gui:<id>, page:, target:, filter:<dim>:<value>, sort:<dim>:<value>,
 * restore:<...>, query:<answer>>
 */
public final class SearchAction extends DeferredNavigationAction {

    private static final String VERB = "search";

    private final PluginGuiManager guis;

    public SearchAction(PluginGuiManager guis) {
        this.guis = guis;
    }

    @Override
    protected @Nullable Destination destinationFor(ActionContext context, Map<String, String> args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "home" -> homeDestination(context, args);
            case "leaderboard" -> leaderboardDestination(context, args);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield null;
            }
        };
    }

    @Override
    protected @Nullable String directValueKey() {
        return "query";
    }

    private @Nullable Destination homeDestination(ActionContext context, Map<String, String> args) {
        String guiId = searchGui(context, args, HomeSearchGui.ROLE);
        return guiId != null ? new HomeDestination(guiId) : null;
    }

    private @Nullable Destination leaderboardDestination(ActionContext context, Map<String, String> args) {
        GuiSession session = context.session();
        String boardId = BaseLeaderboardGui.boardOf(session, guis.layouts().layout(session.definition()));
        if (boardId == null) {
            context.logger().warning("[" + VERB + "] action on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' can't tell which leaderboard to search: the GUI has no 'layout.leaderboard' "
                    + "and wasn't opened from a leaderboard screen - it will not do anything until this is fixed");
            return null;
        }
        String guiId = searchGui(context, args, LeaderboardSearchGui.ROLE);
        return guiId != null ? new LeaderboardDestination(guiId, boardId) : null;
    }

    // Destination resolution

    private @Nullable String searchGui(ActionContext context, Map<String, String> args, String role) {
        String explicit = args.get("gui");
        if (explicit != null && !explicit.isBlank()) {
            return explicitGui(context, explicit.trim(), role);
        }
        if (guis.declaresRole(context.guiId(), role)) {
            return context.guiId(); // already on a search screen of this type
        }
        return switch (guis.resolveRole(role)) {
            case PluginGuiManager.RoleResolution.Found(String guiId) -> guiId;

            case PluginGuiManager.RoleResolution.NotFound() -> {
                context.logger().warning("[" + VERB + "] action on item '" + context.itemKey() + "' in GUI '"
                        + context.guiId() + "' has no loaded GUI declaring 'role: " + role
                        + "' to land on - it will not do anything until one is configured");
                yield null;
            }

            case PluginGuiManager.RoleResolution.Ambiguous(List<String> guiIds) -> {
                context.logger().warning("[" + VERB + "] action on item '" + context.itemKey() + "' in GUI '"
                        + context.guiId() + "' can't pick a default destination: more than one loaded GUI declares 'role: "
                        + role + "' (" + guiIds + ") - add 'gui:<id>' to pick one");
                yield null;
            }
        };
    }

    private @Nullable String explicitGui(ActionContext context, String guiId, String role) {
        if (!GuiActions.guiEnabled(context.manager(), guiId)) {
            context.logger().warning("[" + VERB + "] action on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' points 'gui:' at '" + guiId + "', which isn't a registered/enabled GUI - "
                    + "it will not do anything until this is fixed");
            return null;
        }
        if (!guis.declaresRole(guiId, role)) {
            context.logger().warning("[" + VERB + "] action on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' points 'gui:' at '" + guiId + "', which doesn't declare 'role: " + role
                    + "' and would render without search results - it will not do anything until this is fixed");
            return null;
        }
        return guiId;
    }

    private record HomeDestination(String guiId) implements Destination {
        @Override
        public String inputKey() {
            return "homes.search";
        }

        @Override
        public Map<String, Object> attributesFor(String answer) {
            String query = normalize(answer);
            return query != null ? Map.of(BaseHomeGui.ATTR_SEARCH_QUERY, query) : Map.of();
        }
    }

    // the board always travels with the destination, the query only when there is one
    private record LeaderboardDestination(String guiId, String boardId) implements Destination {
        @Override
        public String inputKey() {
            return "leaderboards.search";
        }

        @Override
        public Map<String, Object> attributesFor(String answer) {
            String query = normalize(answer);
            return query != null
                    ? Map.of(BaseLeaderboardGui.ATTR_BOARD, boardId, BaseLeaderboardGui.ATTR_SEARCH_QUERY, query)
                    : Map.of(BaseLeaderboardGui.ATTR_BOARD, boardId);
        }
    }

    private static @Nullable String normalize(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}