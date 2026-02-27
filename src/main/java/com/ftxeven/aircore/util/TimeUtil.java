package com.ftxeven.aircore.util;

import com.ftxeven.aircore.AirCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Map<Long, String> CACHE = new ConcurrentHashMap<>();
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([dhmst]?)");

    private TimeUtil() {}

    public static Long parseDurationTicks(String input) {
        Matcher matcher = TOKEN.matcher(input);
        long totalTicks = 0;
        boolean matchedAny = false;

        while (matcher.find()) {
            matchedAny = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "d" -> totalTicks += value * 86400L * 20;
                case "h" -> totalTicks += value * 3600L * 20;
                case "m" -> totalTicks += value * 60L * 20;
                case "s" -> totalTicks += value * 20;
                default -> totalTicks += value;
            }
        }

        if (!matchedAny || !matcher.reset().matches()) {
            return null;
        }

        return totalTicks;
    }

    public static Long parseDurationSeconds(String input) {
        Matcher matcher = TOKEN.matcher(input);
        long totalSeconds = 0;
        boolean matchedAny = false;

        while (matcher.find()) {
            matchedAny = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "d" -> totalSeconds += value * 86400L;
                case "h" -> totalSeconds += value * 3600L;
                case "m" -> totalSeconds += value * 60L;
                case "s", "" -> totalSeconds += value;
                default -> {
                    return null;
                }
            }
        }

        if (!matchedAny || !matcher.reset().matches()) {
            return null;
        }

        return totalSeconds;
    }

    public static String formatSeconds(AirCore plugin, long seconds) {
        return CACHE.computeIfAbsent(seconds, s -> compute(plugin, s));
    }

    private static String compute(AirCore plugin, long seconds) {
        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        seconds %= 60;

        String day = plugin.lang().get("utilities.time.placeholders.day");
        String daysStr = plugin.lang().get("utilities.time.placeholders.days");
        String hour = plugin.lang().get("utilities.time.placeholders.hour");
        String hoursStr = plugin.lang().get("utilities.time.placeholders.hours");
        String minute = plugin.lang().get("utilities.time.placeholders.minute");
        String minutesStr = plugin.lang().get("utilities.time.placeholders.minutes");
        String second = plugin.lang().get("utilities.time.placeholders.second");
        String secondsStr = plugin.lang().get("utilities.time.placeholders.seconds");

        String mode = plugin.config().timeFormatMode();
        int granularity = plugin.config().timeFormatGranularity();

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + (days == 1 ? day : daysStr));
        if (hours > 0) parts.add(hours + (hours == 1 ? hour : hoursStr));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? minute : minutesStr));
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + (seconds == 1 ? second : secondsStr));

        return switch (mode) {
            case "SEQUENTIAL" -> parts.getFirst();
            case "CUSTOM" -> String.join(" ", parts.subList(0, Math.min(granularity, parts.size())));
            default -> String.join(" ", parts);
        };
    }
}
