package com.ftxeven.aircore.core.economy.service;

import com.ftxeven.aircore.AirCore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class FormatService {
    private final AirCore plugin;

    public FormatService(AirCore plugin) {
        this.plugin = plugin;
    }

    public double round(double value) {
        if (!plugin.config().economyAllowDecimals()) {
            return Math.floor(value);
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public Double parseAmount(String input) {
        boolean allowShort = plugin.config().economyAllowFormatShortInCommand();
        boolean allowDecimals = plugin.config().economyAllowDecimals();
        if (input == null || input.isBlank()) return null;

        String s = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (allowShort) {
                List<String> suffixes = plugin.config().economyFormatShortSuffixes();
                for (int i = suffixes.size() - 1; i > 0; i--) {
                    String suffix = suffixes.get(i).toLowerCase(Locale.ROOT);
                    if (suffix.isEmpty()) continue;
                    if (s.endsWith(suffix)) {
                        String numberPart = s.substring(0, s.length() - suffix.length());
                        if (numberPart.isEmpty()) return null;
                        BigDecimal base = new BigDecimal(numberPart);
                        BigDecimal value = base.multiply(BigDecimal.valueOf(Math.pow(1000, i)));
                        if (!allowDecimals && value.stripTrailingZeros().scale() > 0) return null;
                        return value.doubleValue();
                    }
                }
            }
            BigDecimal bd = new BigDecimal(s);
            if (!allowDecimals && bd.stripTrailingZeros().scale() > 0) return null;
            return bd.doubleValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String formatAmount(double amount) {
        String fmt = plugin.config().economyNumberFormat();
        boolean allowDecimals = plugin.config().economyAllowDecimals();

        boolean negative = amount < 0;
        double abs = Math.abs(amount);

        boolean originalHasFraction = BigDecimal.valueOf(abs).remainder(BigDecimal.ONE)
                .compareTo(BigDecimal.ZERO) != 0;

        switch (fmt.toUpperCase(Locale.ROOT)) {
            case "RAW" -> {
                double value = allowDecimals ? round(abs) : Math.floor(abs);
                if (allowDecimals && originalHasFraction) {
                    return (negative ? "-" : "") + BigDecimal.valueOf(value)
                            .stripTrailingZeros().toPlainString();
                }
                return (negative ? "-" : "") + (long) value;
            }

            case "SHORT" -> {
                List<String> suffixes = plugin.config().economyFormatShortSuffixes();
                if (suffixes.isEmpty()) return (negative ? "-" : "") + (long) abs;

                int index = 0;
                double value = abs;

                // divide until value < 1000, or we reach the last suffix
                while (value >= 1000 && index + 1 < suffixes.size()) {
                    value /= 1000.0;
                    index++;
                }

                // Round the value to 2 decimals to avoid exactly 1000
                value = BigDecimal.valueOf(value)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

                // If rounding made it 1000, promote to next suffix
                if (value >= 1000 && index + 1 < suffixes.size()) {
                    value /= 1000.0;
                    index++;
                    value = BigDecimal.valueOf(value)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();
                }

                String suffix = suffixes.get(Math.min(index, suffixes.size() - 1));
                BigDecimal bd = BigDecimal.valueOf(value)
                        .stripTrailingZeros();

                return (negative ? "-" : "") + bd.toPlainString() + suffix;
            }

            case "FORMATTED" -> {
                double value = allowDecimals ? round(abs) : Math.floor(abs);
                NumberFormat nf = NumberFormat.getInstance(Locale.US);
                nf.setGroupingUsed(true);
                nf.setMinimumFractionDigits(0);
                nf.setMaximumFractionDigits(allowDecimals && originalHasFraction ? 2 : 0);
                return (negative ? "-" : "") + nf.format(value);
            }

            default -> {
                double value = allowDecimals ? round(abs) : Math.floor(abs);
                NumberFormat nf = NumberFormat.getInstance(Locale.US);
                nf.setGroupingUsed(true);
                nf.setMinimumFractionDigits(0);
                nf.setMaximumFractionDigits(0);
                return (negative ? "-" : "") + nf.format(value);
            }
        }
    }

    public String formatShort(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);

        String[] suffixes = {"", "k", "M", "B", "T"};
        int index = 0;
        while (abs >= 1000 && index < suffixes.length - 1) {
            abs /= 1000.0;
            index++;
        }

        BigDecimal bd = BigDecimal.valueOf(abs).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return (negative ? "-" : "") + bd.toPlainString() + suffixes[index];
    }

    public String formatDetailed(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(true);
        nf.setMaximumFractionDigits(plugin.config().economyAllowDecimals() ? 2 : 0);
        nf.setMinimumFractionDigits(0);

        return (negative ? "-" : "") + nf.format(abs);
    }

    public String formatRaw(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);

        boolean allowDecimals = plugin.config().economyAllowDecimals();
        double value = allowDecimals ? round(abs) : Math.floor(abs);

        BigDecimal bd = BigDecimal.valueOf(value).stripTrailingZeros();

        return (negative ? "-" : "") + bd.toPlainString();
    }
}