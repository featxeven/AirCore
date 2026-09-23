package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.service.LeaderboardService;
import com.ftxeven.aircore.service.LeaderboardService.Snapshot;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LeaderboardPlaceholders {

    private static final Pattern TOP_ENTRY = Pattern.compile("top_(\\d+)_(.+)");

    private final LeaderboardService leaderboards;
    private final ConfigManager configs;
    private final EconomyModule economy;
    private final PlayerService players;

    LeaderboardPlaceholders(LeaderboardService leaderboards, ConfigManager configs, EconomyModule economy, PlayerService players) {
        this.leaderboards = leaderboards;
        this.configs = configs;
        this.economy = economy;
        this.players = players;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        Match match = matchBoard(key.toLowerCase(Locale.ROOT));
        if (match == null) {
            return null;
        }

        Matcher topEntry = TOP_ENTRY.matcher(match.stat());
        if (topEntry.matches()) {
            return resolveTop(match.boardId(), Integer.parseInt(topEntry.group(1)), topEntry.group(2));
        }
        return resolveBoard(viewer, match.boardId(), match.stat());
    }

    // %total% %interval% %viewer_rank% and the %viewer_value% family
    private @Nullable String resolveBoard(@Nullable OfflinePlayer viewer, String boardId, String stat) {
        Snapshot snapshot = leaderboards.snapshot(boardId);
        UUID viewerUuid = viewer != null ? viewer.getUniqueId() : null;
        Map<String, String> placeholders = new HashMap<>();
        GuiPlaceholders.formatBoard(placeholders, configs, snapshot, boardId, viewerUuid, economy);
        return placeholders.get(stat);
    }

    // %holder% %holder_realname% and the %value% family for one ranked position
    private @Nullable String resolveTop(String boardId, int rank, String field) {
        if (rank < 1) {
            return null;
        }
        Snapshot snapshot = leaderboards.snapshot(boardId);
        Map<String, String> placeholders = new HashMap<>();
        if (rank <= snapshot.size()) {
            GuiPlaceholders.formatEntry(placeholders, snapshot.entries().get(rank - 1), players, economy);
        } else {
            GuiPlaceholders.formatVacant(placeholders, rank, configs);
        }
        return placeholders.get(field);
    }

    private record Match(String boardId, String stat) {}

    private @Nullable Match matchBoard(String lowerKey) {
        String bestId = null;
        for (String id : configs.leaderboards().leaderboards().keySet()) {
            String candidate = id.toLowerCase(Locale.ROOT);
            boolean matches = lowerKey.equals(candidate) || lowerKey.startsWith(candidate + "_");
            if (matches && (bestId == null || id.length() > bestId.length())) {
                bestId = id;
            }
        }
        if (bestId == null) {
            return null;
        }
        String stat = lowerKey.length() > bestId.length() ? lowerKey.substring(bestId.length() + 1) : "";
        return new Match(bestId, stat);
    }
}