package com.ftxeven.aircore.core.command;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class FlagTokens {

    private FlagTokens() {
    }

    public record Flag(String key, List<String> values) {

        public static Flag any(String key) {
            return new Flag(key, List.of());
        }

        public static Flag bool(String key) {
            return new Flag(key, List.of("true", "false"));
        }
    }

    public sealed interface Token {
        record Pair(String key, String value) implements Token {}
        record Unknown(String raw) implements Token {}
    }

    public static Token read(String raw, List<Flag> flags) {
        int colon = raw.indexOf(':');
        if (!raw.startsWith("-") || colon < 2) {
            return new Token.Unknown(raw);
        }
        String key = raw.substring(1, colon).toLowerCase(Locale.ROOT);
        boolean known = flags.stream().anyMatch(flag -> flag.key().equals(key));
        return known ? new Token.Pair(key, raw.substring(colon + 1)) : new Token.Unknown(raw);
    }

    public static List<String> suggestTokens(List<Flag> flags, String[] priorTokens, String currentToken,
                                             Function<String, List<String>> dynamicValues) {
        if (!currentToken.isEmpty() && !currentToken.startsWith("-")) {
            return List.of();
        }
        Set<String> usedKeys = usedKeys(priorTokens);
        int colon = currentToken.indexOf(':');
        return colon < 0
                ? suggestKeys(flags, usedKeys, currentToken)
                : suggestValues(flags, usedKeys, currentToken.substring(1, colon), currentToken.substring(colon + 1), dynamicValues);
    }

    private static Set<String> usedKeys(String[] priorTokens) {
        Set<String> used = new HashSet<>();
        for (String token : priorTokens) {
            int colon = token.indexOf(':');
            if (token.startsWith("-") && colon >= 2) {
                used.add(token.substring(1, colon).toLowerCase(Locale.ROOT));
            }
        }
        return used;
    }

    private static List<String> suggestKeys(List<Flag> flags, Set<String> usedKeys, String currentToken) {
        String typed = currentToken.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Flag flag : flags) {
            if (usedKeys.contains(flag.key())) {
                continue;
            }
            String candidate = "-" + flag.key() + ":";
            if (candidate.startsWith(typed)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static List<String> suggestValues(List<Flag> flags, Set<String> usedKeys, String rawKey, String typedValue,
                                              Function<String, List<String>> dynamicValues) {
        String key = rawKey.toLowerCase(Locale.ROOT);
        if (usedKeys.contains(key)) {
            return List.of(); // already specified in an earlier token
        }

        List<String> dynamic = dynamicValues.apply(key);
        if (!dynamic.isEmpty()) {
            return matching(key, dynamic, typedValue);
        }

        for (Flag flag : flags) {
            if (flag.key().equals(key)) {
                return matching(key, flag.values(), typedValue); // empty for a free-form flag
            }
        }
        return List.of(); // unknown key
    }

    private static List<String> matching(String key, List<String> values, String typedValue) {
        String typedLower = typedValue.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(typedLower)) {
                matches.add("-" + key + ":" + value);
            }
        }
        return matches;
    }

    public static @Nullable Boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (value.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }
}