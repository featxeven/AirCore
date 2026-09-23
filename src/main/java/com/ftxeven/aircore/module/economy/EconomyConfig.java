package com.ftxeven.aircore.module.economy;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EconomyConfig extends BaseConfig {

    private volatile boolean enabled;
    private volatile General general;
    private volatile Balance balance;
    private volatile Baltop baltop;
    private volatile Pay pay;
    private volatile Tax tax;
    private volatile Gui gui;

    public EconomyConfig(JavaPlugin plugin) {
        super(plugin, "modules/economy.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        enabled = getBoolean(yaml, "enabled", true);
        general = readGeneral(yaml.getConfigurationSection("general"));
        balance = readBalance(yaml.getConfigurationSection("balance"));
        baltop = readBaltop(yaml.getConfigurationSection("baltop"));
        pay = readPay(yaml.getConfigurationSection("pay"));
        tax = readTax(yaml.getConfigurationSection("tax"));
        gui = readGui(yaml.getConfigurationSection("gui"));
    }

    public boolean enabled() { return enabled; }
    public General general() { return general; }
    public Balance balance() { return balance; }
    public Baltop baltop() { return baltop; }
    public Pay pay() { return pay; }
    public Tax tax() { return tax; }
    public Gui gui() { return gui; }

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getString(sec, "format", "<green>$%amount%</green>"),
                getBoolean(sec, "allow-decimals", true),
                enumOr(sec, "number-format", NumberFormat.class, NumberFormat.FORMATTED),
                getStringList(sec, "format-short-suffix", List.of("", "k", "M", "B", "T", "Q")),
                getBoolean(sec, "allow-shorthand-input", true),
                enumOr(sec, "decimal-handling", DecimalHandling.class, DecimalHandling.REJECT)
        );
    }

    private Balance readBalance(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Balance(getDouble(sec, "default-balance", 500), getDouble(sec, "min-balance", 0), getDouble(sec, "max-balance", 1000000000));
    }

    private Baltop readBaltop(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Baltop(getInt(sec, "entries", 10), getDouble(sec, "min-balance", -1));
    }

    private Pay readPay(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Pay(
                getBoolean(sec, "require-confirmation", true),
                getInt(sec, "confirmation-timeout", 10),
                getBoolean(sec, "allow-self-pay", false),
                getDouble(sec, "min-pay-amount", 3),
                getDouble(sec, "max-pay-amount", 1000000),
                readPayNotifications(sec.getConfigurationSection("notifications"))
        );
    }

    private PayNotifications readPayNotifications(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new PayNotifications(getBoolean(sec, "enabled", true), getInt(sec, "join-delay", 40), getDouble(sec, "min-amount", 0));
    }

    private Tax readTax(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Tax(
                getBoolean(sec, "enabled", false),
                enumOr(sec, "type", TaxType.class, TaxType.PERCENTAGE),
                getDouble(sec, "default-value", 10),
                getDouble(sec, "min-tax", 1),
                getDouble(sec, "max-tax", 1000)
        );
    }

    private Gui readGui(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Gui(readSell(sec.getConfigurationSection("sell")));
    }

    private Sell readSell(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Sell(getBoolean(sec, "skip-invalid-items", false));
    }

    // Section types

    public record General(String format, boolean allowDecimals, NumberFormat numberFormat, List<String> formatShortSuffix, boolean allowShorthandInput, DecimalHandling decimalHandling) {}

    public enum NumberFormat { SHORT, FORMATTED, RAW }

    public enum DecimalHandling { FLOOR, REJECT }

    public record Balance(double defaultBalance, double minBalance, double maxBalance) {}

    public record Baltop(int maxEntries, double minBalance) {}

    public record PayNotifications(boolean enabled, int joinDelay, double minAmount) {}

    public record Pay(boolean requireConfirmation, int confirmationTimeout, boolean allowSelfPay, double minPayAmount, double maxPayAmount, PayNotifications notifications) {}

    public enum TaxType { PERCENTAGE, FIXED }

    public record Tax(boolean enabled, TaxType type, double defaultValue, double minTax, double maxTax) {}

    public record Gui(Sell sell) {}

    public record Sell(boolean skipInvalidItems) {}
}