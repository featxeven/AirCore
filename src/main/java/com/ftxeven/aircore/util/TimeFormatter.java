package com.ftxeven.aircore.util;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.config.MainConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class TimeFormatter {

    private static final String[] UNITS = {"day", "hour", "minute", "second", "tick"};

    private static final long DAY_TICKS = 24_000L;
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final double SECONDS_PER_TICK = (double) SECONDS_PER_DAY / DAY_TICKS; // 3.6
    private static final long DAY_OFFSET_SECONDS = 6L * 3600; // tick 0 == 06:00
    private static final long NANOS_PER_TICK = 50_000_000L; // 50ms, matches the tick length used by ticks()

    private TimeFormatter() {
    }

    // Date / time

    public static String date(Instant instant, MainConfig.Formatting formatting) {
        return formatting.date().format(instant);
    }

    public static String time(Instant instant, MainConfig.Formatting formatting) {
        return formatting.time().format(instant);
    }

    // Duration

    public static String duration(Duration span, MainConfig.Formatting formatting, LangConfig lang) {
        return render(span.isNegative() ? Duration.ZERO : span, formatting.duration(), lang, false);
    }

    public static String duration(Instant until, MainConfig.Formatting formatting, LangConfig lang) {
        return duration(Duration.between(Instant.now(), until), formatting, lang);
    }

    public static String duration(double seconds, MainConfig.Formatting formatting, LangConfig lang) {
        return duration(Duration.ofNanos(Math.round(seconds * 1_000_000_000L)), formatting, lang);
    }

    // a tick delta rendered as a duration, down to tick-level precision
    public static String ticks(long ticks, MainConfig.Formatting formatting, LangConfig lang) {
        Duration span = Duration.ofMillis(ticks * 50L);
        return render(span.isNegative() ? Duration.ZERO : span, formatting.duration(), lang, true);
    }

    // a raw tick count with a pluralized unit
    public static String tickCount(long ticks, LangConfig lang) {
        return ticks + label(lang, "tick", ticks);
    }

    public static String clock(long ticksOfDay, MainConfig.Formatting formatting) {
        long normalized = Math.floorMod(ticksOfDay, DAY_TICKS);
        long totalSeconds = Math.floorMod(DAY_OFFSET_SECONDS + Math.round(normalized * SECONDS_PER_TICK), SECONDS_PER_DAY);
        ZonedDateTime zoned = LocalDate.EPOCH.atTime(LocalTime.ofSecondOfDay(totalSeconds)).atZone(formatting.timezone());
        return formatting.time().format(zoned);
    }

    public static String expiresIn(int expireAfterSeconds, MainConfig.Formatting formatting, LangConfig lang) {
        return expireAfterSeconds > 0
                ? duration(expireAfterSeconds, formatting, lang)
                : lang.get("placeholders.never").getFirst();
    }

    private static Duration roundUpToSeconds(Duration span) {
        int nanos = span.getNano();
        return nanos == 0 ? span : span.plusNanos(1_000_000_000L - nanos);
    }

    private static String render(Duration span, MainConfig.DurationStyle style, LangConfig lang, boolean includeTicks) {
        Duration whole = includeTicks ? span : roundUpToSeconds(span);
        long[] values = includeTicks
                ? new long[]{whole.toDaysPart(), whole.toHoursPart(), whole.toMinutesPart(), whole.toSecondsPart(), whole.toNanosPart() / NANOS_PER_TICK}
                : new long[]{whole.toDaysPart(), whole.toHoursPart(), whole.toMinutesPart(), whole.toSecondsPart()};

        int limit = switch (style.mode()) {
            case DETAILED -> values.length;
            case SEQUENTIAL -> 1;
            case CUSTOM -> style.granularity();
        };

        StringBuilder joined = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < values.length && shown < limit; i++) {
            if (values[i] <= 0) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(' ');
            }
            joined.append(values[i]).append(label(lang, UNITS[i], values[i]));
            shown++;
        }

        return shown > 0 ? joined.toString() : "0" + label(lang, UNITS[values.length - 1], 0);
    }

    private static String label(LangConfig lang, String unit, long value) {
        String key = "placeholders.time." + unit + (value == 1 ? "" : "s");
        return lang.get(key).getFirst();
    }
}