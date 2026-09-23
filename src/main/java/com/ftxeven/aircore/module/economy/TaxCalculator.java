package com.ftxeven.aircore.module.economy;

import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.permissions.Permissible;

import java.util.function.Supplier;

public final class TaxCalculator {

    private final Supplier<EconomyConfig> configs;

    public TaxCalculator(Supplier<EconomyConfig> configs) {
        this.configs = configs;
    }

    public double calculate(Permissible payer, double amount) {
        EconomyConfig.Tax tax = configs.get().tax();
        if (!tax.enabled() || payer.hasPermission(Permissions.Bypass.TAX)) {
            return 0;
        }

        double rate = PermissionTiers.resolve(payer, Permissions.Bypass.TAX, PermissionTiers.Pick.LOWEST)
                .orElse(tax.defaultValue());

        double raw = tax.type() == EconomyConfig.TaxType.PERCENTAGE
                ? clamp(amount * (rate / 100.0), tax.minTax(), tax.maxTax())
                : rate;

        return configs.get().general().allowDecimals() ? raw : Math.round(raw);
    }

    private static double clamp(double amount, double min, double max) {
        return max < 0 ? Math.max(amount, min) : Math.clamp(amount, min, max);
    }
}