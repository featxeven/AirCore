package com.ftxeven.aircore.module.economy.format;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.util.MiniText;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class AmountFormatter {

    private final Supplier<EconomyConfig> configs;
    private final Supplier<LangConfig> lang;

    public AmountFormatter(Supplier<EconomyConfig> configs, Supplier<LangConfig> lang) {
        this.configs = configs;
        this.lang = lang;
    }

    // Styled text only

    public String format(double amount) {
        EconomyConfig.General general = configs.get().general();
        String number = NumberConverter.format(amount, general.numberFormat(), general.formatShortSuffix(), general.allowDecimals());
        return general.format().replace("%amount%", number);
    }

    // Writes %key%, %key_plain%, %key_number% and %key_raw% for an amount that's always present
    public void formatInto(Map<String, String> placeholders, String key, double amount) {
        putAmount(placeholders, key, OptionalDouble.of(amount), null);
    }

    // Same 4 placeholders, but renders as the configured empty text (e.g. "-") when the value is
    // zero/waived instead of showing "$0" - used for fee/tax-style amounts
    public void formatOptionalInto(Map<String, String> placeholders, String key, double amount, String emptyLangKey) {
        OptionalDouble present = amount > 0 ? OptionalDouble.of(amount) : OptionalDouble.empty();
        putAmount(placeholders, key, present, emptyLangKey);
    }

    // Rejects zero/negative amounts
    public OptionalDouble parse(String raw) {
        return parse(raw, true);
    }

    // Same parsing/rounding rules as parse(), but allows zero and negative values
    public OptionalDouble parseSigned(String raw) {
        return parse(raw, false);
    }

    private OptionalDouble parse(String raw, boolean requirePositive) {
        EconomyConfig.General general = configs.get().general();
        OptionalDouble parsed = NumberConverter.parse(raw, general.allowShorthandInput(), general.formatShortSuffix());
        if (parsed.isEmpty()) {
            return OptionalDouble.empty();
        }
        double value = parsed.getAsDouble();
        if (!general.allowDecimals() && hasFraction(value)) {
            if (general.decimalHandling() == EconomyConfig.DecimalHandling.REJECT) {
                return OptionalDouble.empty();
            }
            value = Math.floor(value);
        }
        return !requirePositive || value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    private void putAmount(Map<String, String> placeholders, String key, OptionalDouble amount, String emptyLangKey) {
        if (amount.isEmpty()) {
            String empty = emptyLangKey != null ? lang.get().get(emptyLangKey).getFirst() : "";
            String emptyPlain = MiniText.plain(empty);
            placeholders.put(key, empty);
            placeholders.put(key + "_plain", emptyPlain);
            placeholders.put(key + "_number", emptyPlain);
            placeholders.put(key + "_raw", emptyPlain);
            return;
        }
        double value = amount.getAsDouble();
        EconomyConfig.General general = configs.get().general();
        String number = NumberConverter.format(value, general.numberFormat(), general.formatShortSuffix(), general.allowDecimals());
        String raw = NumberConverter.format(value, EconomyConfig.NumberFormat.RAW, general.formatShortSuffix(), general.allowDecimals());
        String formatted = general.format().replace("%amount%", number);
        placeholders.put(key, formatted);
        placeholders.put(key + "_plain", MiniText.plain(formatted));
        placeholders.put(key + "_number", number);
        placeholders.put(key + "_raw", raw);
    }

    private static boolean hasFraction(double value) {
        return value != Math.floor(value);
    }
}