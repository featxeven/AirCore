package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.format.AmountFormatter;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.service.LeaderboardService.Entry;
import com.ftxeven.aircore.service.LeaderboardService.Snapshot;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiPlaceholders {

    private static final String LEADERBOARD_EMPTY_TEXT_KEY = "placeholders.empty.leaderboard";

    private GuiPlaceholders() {
    }

    // Generic

    public static void write(GuiSession session, PlayerService players) {
        UUID target = session.target();
        if (target != null) {
            players.formatDisplayName(session.placeholders(), "target", target);
        }
    }

    public static Map<String, String> combine(GuiSession session, Map<String, String> specific) {
        Map<String, String> combined = new LinkedHashMap<>(session.placeholders());
        combined.putAll(specific);
        return combined;
    }

    // Homes

    public static UUID resolveOwner(@Nullable UUID sessionTarget, Player viewer) {
        return sessionTarget != null ? sessionTarget : viewer.getUniqueId();
    }

    public static Map<String, String> home(Home home, MainConfig.Formatting formatting) {
        Position position = home.position();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("home", home.name());
        placeholders.put("name", home.name());
        placeholders.put("world", position.world());
        placeholders.put("x", blockCoordinate(position.x()));
        placeholders.put("y", blockCoordinate(position.y()));
        placeholders.put("z", blockCoordinate(position.z()));
        placeholders.put("date", TimeFormatter.date(home.createdAt(), formatting));
        placeholders.put("time", TimeFormatter.time(home.createdAt(), formatting));
        placeholders.put("favorite", String.valueOf(home.favorite()));
        return placeholders;
    }

    public static Map<String, String> icon(FilterConfig filter, @Nullable FilterConfig.HomeIcon icon) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("icon", icon != null ? icon.label() : filter.homeIconLabel(null));
        placeholders.put("icon_id", icon != null ? icon.id() : FilterConfig.ALL_ID);
        placeholders.put("bundle", icon != null && icon.bundleId() != null ? filter.bundleLabel(icon.bundleId()) : "");
        placeholders.put("bundle_id", icon != null && icon.bundleId() != null ? icon.bundleId() : "");
        return placeholders;
    }

    public static Map<String, String> homeIcon(FilterConfig filter, @Nullable Home home) {
        String rawIcon = home != null ? home.icon() : null;
        return icon(filter, rawIcon != null ? filter.homeIcon(rawIcon).orElse(null) : null);
    }

    // home() + homeIcon() combined
    public static Map<String, String> homeWithIcon(ConfigManager configs, Home home) {
        Map<String, String> placeholders = new LinkedHashMap<>(home(home, configs.main().formatting()));
        placeholders.putAll(homeIcon(configs.filter(), home));
        return placeholders;
    }

    public static Map<String, String> iconGridState(FilterConfig.HomeIcon icon, @Nullable Home home, Permissible viewer) {
        String current = home != null ? home.icon() : null;
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("icon_selected", String.valueOf(icon.id().equals(current)));
        placeholders.put("icon_locked", String.valueOf(!icon.isAccessibleTo(viewer)));
        return placeholders;
    }

    private static String blockCoordinate(double value) {
        return String.valueOf((long) Math.floor(value));
    }

    // Leaderboards

    public static void formatEntry(Map<String, String> placeholders, Entry entry, PlayerService players, EconomyModule economy) {
        placeholders.put("rank", String.valueOf(entry.rank()));
        players.formatDisplayName(placeholders, "holder", entry.holder());
        putValue(placeholders, "value", entry, economy);
    }

    public static void formatVacant(Map<String, String> placeholders, int rank, ConfigManager configs) {
        String empty = configs.lang().get(LEADERBOARD_EMPTY_TEXT_KEY).getFirst();
        placeholders.put("rank", String.valueOf(rank));
        placeholders.put("holder", empty);
        placeholders.put("holder_realname", empty);
        putText(placeholders, "value", empty, MiniText.plain(empty));
    }

    public static void formatBoard(Map<String, String> placeholders, ConfigManager configs, Snapshot snapshot,
                                   String boardId, @Nullable UUID viewer, EconomyModule economy) {
        placeholders.put("total", String.valueOf(snapshot.size()));
        configs.leaderboards().leaderboard(boardId).ifPresent(board -> placeholders.put("interval",
                TimeFormatter.duration(board.refreshInterval(), configs.main().formatting(), configs.lang())));

        Entry own = viewer != null ? snapshot.byHolder().get(viewer) : null;
        if (own == null) {
            String empty = configs.lang().get(LEADERBOARD_EMPTY_TEXT_KEY).getFirst();
            placeholders.put("viewer_rank", empty);
            putText(placeholders, "viewer_value", empty, MiniText.plain(empty));
            return;
        }
        placeholders.put("viewer_rank", String.valueOf(own.rank()));
        putValue(placeholders, "viewer_value", own, economy);
    }

    private static void putValue(Map<String, String> placeholders, String key, Entry entry, EconomyModule economy) {
        if (entry.value() == null) {
            economy.formatter().formatInto(placeholders, key, entry.score());
            return;
        }
        putText(placeholders, key, entry.value(), entry.value());
    }

    private static void putText(Map<String, String> placeholders, String key, String styled, String plain) {
        placeholders.put(key, styled);
        placeholders.put(key + "_plain", plain);
        placeholders.put(key + "_number", plain);
        placeholders.put(key + "_raw", plain);
    }

    // Economy

    public static void formatSellAmount(Map<String, String> placeholders, AmountFormatter formatter,
                                        String amountKey, String taxKey, SellHandler.Verdict verdict) {
        if (verdict instanceof SellHandler.Verdict.Sold sold) {
            formatter.formatInto(placeholders, amountKey, sold.amount());
            formatter.formatOptionalInto(placeholders, taxKey, sold.tax(), "placeholders.empty.tax");
            return;
        }
        formatter.formatInto(placeholders, amountKey, 0);
        formatter.formatOptionalInto(placeholders, taxKey, 0, "placeholders.empty.tax");
    }
}