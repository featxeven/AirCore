package com.ftxeven.aircore.module.kits;

import com.ftxeven.aircore.core.command.FlagTokens;
import com.ftxeven.aircore.core.command.FlagTokens.Flag;
import com.ftxeven.aircore.core.command.FlagTokens.Token;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record KitParams(@Nullable Integer cooldownSeconds, @Nullable Boolean oneTime,
                        @Nullable Boolean dropOnFullInventory, @Nullable Boolean exactSlots,
                        @Nullable Boolean requiresPermission) {

    public static final KitParams NONE = new KitParams(null, null, null, null, null);

    public sealed interface Result {
        record Success(KitParams params) implements Result {}
        record UnknownParam(String token) implements Result {}
        record InvalidValue(String param, String value) implements Result {}
    }

    private static final String COOLDOWN = "cooldown";
    private static final String ONE_TIME = "one_time";
    private static final String DROP_ON_FULL_INVENTORY = "drop_on_full_inventory";
    private static final String EXACT_SLOTS = "exact_slots";
    private static final String REQUIRES_PERMISSION = "requires_permission";

    private static final List<Flag> FLAGS = List.of(
            Flag.any(COOLDOWN),
            Flag.bool(ONE_TIME),
            Flag.bool(DROP_ON_FULL_INVENTORY),
            Flag.bool(EXACT_SLOTS),
            Flag.bool(REQUIRES_PERMISSION)
    );

    private static final Pattern DURATION_SEGMENT = Pattern.compile("(\\d+)([a-zA-Z])");
    private static final Map<Character, Integer> DURATION_UNIT_SECONDS = Map.of(
            's', 1, 'm', 60, 'h', 3600, 'd', 86400, 'w', 604800
    );

    public static Result parse(String[] tokens) {
        Integer cooldownSeconds = null;
        Boolean oneTime = null;
        Boolean dropOnFullInventory = null;
        Boolean exactSlots = null;
        Boolean requiresPermission = null;

        for (String raw : tokens) {
            if (!(FlagTokens.read(raw, FLAGS) instanceof Token.Pair(String key, String value))) {
                return new Result.UnknownParam(raw);
            }

            switch (key) {
                case COOLDOWN -> {
                    OptionalInt seconds = parseCooldownSeconds(value);
                    if (seconds.isEmpty()) return new Result.InvalidValue(key, value);
                    cooldownSeconds = seconds.getAsInt();
                }
                case ONE_TIME -> {
                    Boolean parsed = FlagTokens.parseBoolean(value);
                    if (parsed == null) return new Result.InvalidValue(key, value);
                    oneTime = parsed;
                }
                case DROP_ON_FULL_INVENTORY -> {
                    Boolean parsed = FlagTokens.parseBoolean(value);
                    if (parsed == null) return new Result.InvalidValue(key, value);
                    dropOnFullInventory = parsed;
                }
                case EXACT_SLOTS -> {
                    Boolean parsed = FlagTokens.parseBoolean(value);
                    if (parsed == null) return new Result.InvalidValue(key, value);
                    exactSlots = parsed;
                }
                case REQUIRES_PERMISSION -> {
                    Boolean parsed = FlagTokens.parseBoolean(value);
                    if (parsed == null) return new Result.InvalidValue(key, value);
                    requiresPermission = parsed;
                }
            }
        }

        return new Result.Success(new KitParams(cooldownSeconds, oneTime, dropOnFullInventory, exactSlots, requiresPermission));
    }

    public static List<String> suggestTokens(String[] priorTokens, String currentToken) {
        return FlagTokens.suggestTokens(FLAGS, priorTokens, currentToken, key -> List.of());
    }

    private static OptionalInt parseCooldownSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        String compact = raw.trim().replaceAll("\\s+", "");
        return isDigitsOnly(compact) ? safeParseInt(compact) : parseDurationSegments(compact);
    }

    private static OptionalInt parseDurationSegments(String compact) {
        Matcher matcher = DURATION_SEGMENT.matcher(compact);
        Set<Character> seenUnits = new HashSet<>();
        long totalSeconds = 0;
        int consumed = 0;

        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return OptionalInt.empty();
            }

            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            Integer multiplier = DURATION_UNIT_SECONDS.get(unit);
            if (multiplier == null || !seenUnits.add(unit)) {
                return OptionalInt.empty();
            }

            OptionalInt amount = safeParseInt(matcher.group(1));
            if (amount.isEmpty()) {
                return OptionalInt.empty();
            }

            totalSeconds += (long) amount.getAsInt() * multiplier;
            consumed = matcher.end();
        }

        if (consumed != compact.length() || consumed == 0 || totalSeconds > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) totalSeconds);
    }

    private static OptionalInt safeParseInt(String digits) {
        try {
            long value = Long.parseLong(digits);
            return value >= 0 && value <= Integer.MAX_VALUE ? OptionalInt.of((int) value) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static boolean isDigitsOnly(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}