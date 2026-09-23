package com.ftxeven.aircore.module.announcements;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class CronCalculator {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private static final int MAX_DAYS_AHEAD = 370;

    private CronCalculator() {
    }

    static Duration untilNext(List<String> rawTimes, List<String> rawDates, ZoneId zone, Instant now, Consumer<String> onWarning) {
        List<LocalTime> times = parseTimes(rawTimes, onWarning);
        if (times.isEmpty()) {
            return null;
        }

        ZonedDateTime nowZoned = now.atZone(zone);
        for (int dayOffset = 0; dayOffset <= MAX_DAYS_AHEAD; dayOffset++) {
            LocalDate date = nowZoned.toLocalDate().plusDays(dayOffset);
            if (!rawDates.isEmpty() && rawDates.stream().noneMatch(pattern -> matchesDate(date, pattern))) {
                continue;
            }
            for (LocalTime time : times) {
                ZonedDateTime candidate = ZonedDateTime.of(date, time, zone);
                if (candidate.isAfter(nowZoned)) {
                    return Duration.between(nowZoned, candidate);
                }
            }
        }
        return null;
    }

    private static List<LocalTime> parseTimes(List<String> raw, Consumer<String> onWarning) {
        List<LocalTime> times = new ArrayList<>();
        for (String entry : raw) {
            try {
                times.add(LocalTime.parse(entry.trim(), TIME_FORMAT));
            } catch (DateTimeException e) {
                onWarning.accept("invalid CRON time '" + entry + "', expected 24h HH:mm, skipping");
            }
        }
        Collections.sort(times);
        return times;
    }

    private static boolean matchesDate(LocalDate date, String pattern) {
        String[] parts = pattern.trim().split("/");
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            if (parts.length == 2) {
                return date.getDayOfMonth() == day && date.getMonthValue() == month;
            }
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[2]);
                int fullYear = year < 100 ? 2000 + year : year;
                return date.getDayOfMonth() == day && date.getMonthValue() == month && date.getYear() == fullYear;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            return false;
        }
        return false;
    }
}