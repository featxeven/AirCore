package com.ftxeven.aircore.module.economy.sell;

import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.module.economy.TaxCalculator;
import com.ftxeven.aircore.module.economy.balance.BalanceLedger;
import com.ftxeven.aircore.module.economy.worth.WorthCalculator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class SellHandler {

    public sealed interface Verdict {
        record Sold(double amount, double tax, int itemsSold) implements Verdict {}
        record Nothing() implements Verdict {}
        record Invalid(Set<String> items) implements Verdict {}
        record ExceedsMax(double limit) implements Verdict {}
    }

    public record BatchResult(Verdict verdict, boolean[] sold) {}

    private final Supplier<EconomyConfig> configs;
    private final WorthCalculator worth;
    private final BalanceLedger balances;
    private final TaxCalculator taxCalculator;

    public SellHandler(Supplier<EconomyConfig> configs, WorthCalculator worth, BalanceLedger balances, TaxCalculator taxCalculator) {
        this.configs = configs;
        this.worth = worth;
        this.balances = balances;
        this.taxCalculator = taxCalculator;
    }

    // Held item (hand) - /sell

    public Verdict previewHand(Player player) {
        return evaluateHand(player);
    }

    public Verdict sellHand(Player player) {
        Verdict verdict = evaluateHand(player);
        if (verdict instanceof Verdict.Sold sold) {
            player.getInventory().setItemInMainHand(null);
            balances.deposit(player.getUniqueId(), sold.amount());
        }
        return verdict;
    }

    private Verdict evaluateHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return new Verdict.Nothing();
        }

        WorthCalculator.Appraisal appraisal = worth.appraise(held);
        if (!appraisal.sellable()) {
            return new Verdict.Invalid(appraisal.invalidKeys());
        }

        return finalizeVerdict(player, appraisal.total(), held.getAmount());
    }

    // Batch of items

    public Verdict previewBatch(Player player, ItemStack[] items, boolean skipInvalidItems) {
        return evaluateBatch(player, items, skipInvalidItems).verdict();
    }

    public BatchResult sellBatch(Player player, ItemStack[] items, boolean skipInvalidItems) {
        BatchResult result = evaluateBatch(player, items, skipInvalidItems);
        if (result.verdict() instanceof Verdict.Sold sold) {
            balances.deposit(player.getUniqueId(), sold.amount());
        }
        return result;
    }

    private BatchResult evaluateBatch(Player player, ItemStack[] items, boolean skipInvalidItems) {
        boolean[] sold = new boolean[items.length];
        Set<String> invalidKeys = new LinkedHashSet<>();
        double rawTotal = 0;
        int itemsSold = 0;
        boolean blocked = false;

        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            WorthCalculator.Appraisal appraisal = worth.appraise(stack);
            if (!appraisal.sellable()) {
                invalidKeys.addAll(appraisal.invalidKeys());
                blocked |= !skipInvalidItems;
                continue;
            }

            rawTotal += appraisal.total();
            itemsSold += stack.getAmount();
            sold[i] = true;
        }

        if (blocked) {
            return new BatchResult(new Verdict.Invalid(Set.copyOf(invalidKeys)), new boolean[items.length]);
        }
        if (itemsSold == 0) {
            return new BatchResult(new Verdict.Nothing(), new boolean[items.length]);
        }

        Verdict verdict = finalizeVerdict(player, rawTotal, itemsSold);
        return new BatchResult(verdict, verdict instanceof Verdict.Sold ? sold : new boolean[items.length]);
    }

    private Verdict finalizeVerdict(Player player, double rawTotal, int itemsSold) {
        double tax = taxCalculator.calculate(player, rawTotal);
        double amount = round(Math.max(0, rawTotal - tax));
        if (balances.evaluateDeposit(player.getUniqueId(), amount) instanceof BalanceLedger.Verdict.AboveMax aboveMax) {
            return new Verdict.ExceedsMax(aboveMax.max());
        }
        return new Verdict.Sold(amount, tax, itemsSold);
    }

    private double round(double amount) {
        return configs.get().general().allowDecimals() ? amount : Math.round(amount);
    }
}