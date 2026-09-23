package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.core.command.DurationUnits;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

// Shared between /time and /playertime: resolves a typed time value into ticks
final class TimeValueParser {

    private static final Map<String, Long> KEYWORDS = Map.of(
            "day", 1000L,
            "noon", 6000L,
            "night", 13000L,
            "midnight", 18000L
    );

    private TimeValueParser() {
    }

    static OptionalLong resolve(String raw, boolean isDelta, DurationUnits durationUnits, String filter) {
        if (!isDelta) {
            Long keyword = KEYWORDS.get(raw.toLowerCase(Locale.ROOT));
            if (keyword != null) {
                return OptionalLong.of(keyword);
            }
        }
        return durationUnits.parse(raw, filter);
    }
}