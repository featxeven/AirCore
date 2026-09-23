package com.ftxeven.aircore.module.economy.format;

import com.ftxeven.aircore.module.economy.EconomyConfig;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

final class NumberConverter {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final ThreadLocal<Map<String, DecimalFormat>> PATTERNS = ThreadLocal.withInitial(HashMap::new);

    private NumberConverter() {
    }

    static String format(double amount, EconomyConfig.NumberFormat mode, List<String> suffixes, boolean allowDecimals) {
        return switch (mode) {
            case SHORT -> formatShort(amount, suffixes, allowDecimals);
            case FORMATTED -> pattern(allowDecimals ? "#,##0.##" : "#,##0").format(amount);
            case RAW -> pattern(allowDecimals ? "0.##" : "0").format(amount);
        };
    }

    private static String formatShort(double amount, List<String> suffixes, boolean allowDecimals) {
        if (suffixes.isEmpty()) {
            return pattern(allowDecimals ? "0.##" : "0").format(amount);
        }

        double reduced = amount;
        int exponent = 0;
        while (exponent < suffixes.size() - 1 && Math.abs(reduced) >= 1000) {
            reduced /= 1000;
            exponent++;
        }
        return pattern(allowDecimals ? "0.##" : "0").format(reduced) + suffixes.get(exponent);
    }

    static OptionalDouble parse(String raw, boolean allowShorthand, List<String> suffixes) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return OptionalDouble.empty();
        }

        if (allowShorthand) {
            OptionalDouble shorthand = parseShorthand(trimmed, suffixes);
            if (shorthand.isPresent()) {
                return shorthand;
            }
        }

        try {
            double value = Double.parseDouble(trimmed);
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble parseShorthand(String trimmed, List<String> suffixes) {
        for (int exponent = suffixes.size() - 1; exponent >= 1; exponent--) {
            String suffix = suffixes.get(exponent);
            if (suffix.isEmpty() || trimmed.length() <= suffix.length()) {
                continue;
            }
            if (!trimmed.regionMatches(true, trimmed.length() - suffix.length(), suffix, 0, suffix.length())) {
                continue;
            }

            String numberPart = trimmed.substring(0, trimmed.length() - suffix.length()).trim();
            try {
                return OptionalDouble.of(Double.parseDouble(numberPart) * Math.pow(1000, exponent));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    private static DecimalFormat pattern(String pattern) {
        return PATTERNS.get().computeIfAbsent(pattern, p -> new DecimalFormat(p, SYMBOLS));
    }
}