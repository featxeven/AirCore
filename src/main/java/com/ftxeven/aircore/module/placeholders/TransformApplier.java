package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.Transform;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class TransformApplier {

    private TransformApplier() {
    }

    static String apply(String value, Transform transform) {
        String input = value != null ? value : "";
        return switch (transform) {
            case Transform.Uppercase u -> input.toUpperCase(Locale.ROOT);
            case Transform.Lowercase l -> input.toLowerCase(Locale.ROOT);
            case Transform.Capitalize c -> capitalize(input);
            case Transform.Trim t -> input.trim();
            case Transform.Truncate t -> truncate(input, t.length(), t.suffix());
            case Transform.Replace r -> replaceFirst(input, r.from(), r.to());
            case Transform.NumberFormat f -> formatNumber(input, f.pattern());
        };
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int length, String suffix) {
        if (length < 0 || value.length() <= length) {
            return value;
        }
        return value.substring(0, length) + suffix;
    }

    private static String replaceFirst(String value, String from, String to) {
        if (from == null || from.isEmpty()) {
            return value;
        }
        int index = value.indexOf(from);
        return index < 0 ? value : value.substring(0, index) + to + value.substring(index + from.length());
    }

    private static String formatNumber(String value, String pattern) {
        try {
            double number = Double.parseDouble(value.trim());
            return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT)).format(number);
        } catch (NumberFormatException e) {
            return value;
        }
    }
}