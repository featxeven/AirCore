package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.extras.afk.AfkHandler;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;

final class AfkPlaceholders {

    private final AfkHandler afk;
    private final ConfigManager configs;

    AfkPlaceholders(AfkHandler afk, ConfigManager configs) {
        this.afk = afk;
        this.configs = configs;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.equals("count")) {
            return String.valueOf(afk.count());
        }
        if (viewer == null) {
            return null;
        }
        return switch (lower) {
            case "active" -> String.valueOf(afk.isAfk(viewer.getUniqueId()));
            case "auto" -> String.valueOf(afk.isAuto(viewer.getUniqueId()));
            case "reason" -> afk.reason(viewer.getUniqueId()).orElse("");
            case "time" -> TimeFormatter.duration(
                    afk.duration(viewer.getUniqueId()).orElse(Duration.ZERO), configs.main().formatting(), configs.lang());
            case "time_raw" -> String.valueOf(afk.duration(viewer.getUniqueId()).map(Duration::getSeconds).orElse(0L));
            case "since_date" -> afk.since(viewer.getUniqueId())
                    .map(since -> TimeFormatter.date(since, configs.main().formatting())).orElse("");
            case "since_time" -> afk.since(viewer.getUniqueId())
                    .map(since -> TimeFormatter.time(since, configs.main().formatting())).orElse("");
            default -> null;
        };
    }
}