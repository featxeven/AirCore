package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.nav.GuiContext;
import com.ftxeven.aircore.gui.BaseHomeGui;
import com.ftxeven.aircore.gui.BaseLeaderboardGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiCyclers;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class GuiPlaceholders {

    private static final String SEARCH_QUERY_SUFFIX = "-search-query";
    private static final String FILTER_PREFIX = "filter_";
    private static final String SORT_PREFIX = "sort_";
    private static final String ID_SUFFIX = "_id";

    private final PluginGuiManager guis;
    private final ConfigManager configs;
    private final PlayerService players;

    GuiPlaceholders(PluginGuiManager guis, ConfigManager configs, PlayerService players) {
        this.guis = guis;
        this.configs = configs;
        this.players = players;
    }

    @Nullable String resolve(Player viewer, String key) {
        GuiSession session = guis.guis().session(viewer);
        if (session == null) {
            return "";
        }

        if (key.startsWith(FILTER_PREFIX)) {
            return cyclerValue(session, key.substring(FILTER_PREFIX.length()),
                    GuiCyclers::filterAttribute, dimension -> GuiCyclers.filterOptions(configs, dimension));
        }
        if (key.startsWith(SORT_PREFIX)) {
            return cyclerValue(session, key.substring(SORT_PREFIX.length()),
                    GuiCyclers::sortAttribute, dimension -> GuiCyclers.sortOptions(configs, dimension));
        }

        return switch (key) {
            case "id" -> session.definition().id();
            case "previous_id" -> previousId(session);
            case "page" -> String.valueOf(session.page());
            case "pages" -> String.valueOf(session.totalPages());
            case "has_next_page" -> String.valueOf(session.page() < session.totalPages());
            case "has_previous_page" -> String.valueOf(session.page() > 1);
            case "open" -> "true";
            case "target" -> target(session);
            case "target_name" -> targetName(session);
            case "search" -> searchQuery(session);
            case "board" -> board(session);
            case "home" -> homeName(session);
            default -> null;
        };
    }

    private String previousId(GuiSession session) {
        GuiContext back = session.navBack();
        return back != null ? back.screen().guiId() : "";
    }

    private String target(GuiSession session) {
        UUID target = session.target();
        return target != null ? target.toString() : "";
    }

    private String targetName(GuiSession session) {
        UUID target = session.target();
        if (target == null) {
            return "";
        }
        Map<String, String> resolved = new HashMap<>();
        players.formatDisplayName(resolved, "target", target);
        return resolved.getOrDefault("target", "");
    }

    private String searchQuery(GuiSession session) {
        for (Map.Entry<String, String> attribute : session.stringAttributes().entrySet()) {
            if (attribute.getKey().endsWith(SEARCH_QUERY_SUFFIX)) {
                return attribute.getValue();
            }
        }
        return "";
    }

    private String board(GuiSession session) {
        LayoutConfig layout = guis.layouts().layout(session.definition());
        String id = BaseLeaderboardGui.boardOf(session, layout);
        return id != null ? id : "";
    }

    private String homeName(GuiSession session) {
        Home home = session.attribute(BaseHomeGui.ATTR_TARGET_HOME, Home.class);
        return home != null ? home.name() : "";
    }

    private @Nullable String cyclerValue(GuiSession session, String rest, Function<String, String> attributeOf,
                                         Function<String, LinkedHashMap<String, String>> optionsOf) {
        boolean idOnly = rest.endsWith(ID_SUFFIX);
        String dimension = idOnly ? rest.substring(0, rest.length() - ID_SUFFIX.length()) : rest;

        String attributeKey = attributeOf.apply(dimension);
        if (attributeKey == null) {
            return null; // not a recognized dimension
        }
        String selected = session.attribute(attributeKey, String.class);
        if (selected == null) {
            return "";
        }
        if (idOnly) {
            return selected;
        }
        LinkedHashMap<String, String> options = optionsOf.apply(dimension);
        return options != null ? options.getOrDefault(selected, selected) : selected;
    }
}