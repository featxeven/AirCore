package com.ftxeven.aircore.module.economy.pay;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.module.economy.TaxCalculator;
import com.ftxeven.aircore.module.economy.balance.BalanceLedger;
import com.ftxeven.aircore.module.economy.format.AmountFormatter;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.module.extras.afk.AfkHandler;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class PayHandler {

    public sealed interface Verdict {
        record Sent(double amount, double tax, boolean recipientOnline) implements Verdict {}
        record Self() implements Verdict {}
        record Blocked() implements Verdict {}
        record Disabled() implements Verdict {}
        record BelowMin(double min) implements Verdict {}
        record AboveMax(double max) implements Verdict {}
        record InsufficientFunds(double missing) implements Verdict {}
        record ExceedsRecipientMax(double limit) implements Verdict {}
    }

    private final Supplier<EconomyConfig> configs;
    private final PlayerService players;
    private final BalanceLedger balances;
    private final BlockHandler blocks;
    private final AfkHandler afk;
    private final TaxCalculator taxCalculator;

    public PayHandler(Supplier<EconomyConfig> configs, PlayerService players, BalanceLedger balances, BlockHandler blocks,
                      AfkHandler afk, TaxCalculator taxCalculator) {
        this.configs = configs;
        this.players = players;
        this.balances = balances;
        this.blocks = blocks;
        this.afk = afk;
        this.taxCalculator = taxCalculator;
    }

    public Optional<Verdict> preview(Player sender, UUID targetUuid, double amount) {
        EconomyConfig.Pay pay = configs.get().pay();

        if (!pay.allowSelfPay() && sender.getUniqueId().equals(targetUuid)) {
            return Optional.of(new Verdict.Self());
        }

        if (blocks.blocksInteraction(ExtrasConfig.BlockAction.PAYMENTS, targetUuid, sender)) {
            return Optional.of(new Verdict.Blocked());
        }

        if (amount < pay.minPayAmount()) {
            return Optional.of(new Verdict.BelowMin(pay.minPayAmount()));
        }
        if (pay.maxPayAmount() != -1 && amount > pay.maxPayAmount()) {
            return Optional.of(new Verdict.AboveMax(pay.maxPayAmount()));
        }

        boolean acceptsPayments = players.find(targetUuid).map(profile -> profile.toggles().pay()).orElse(true);
        if (!acceptsPayments && !sender.hasPermission(Permissions.Bypass.PAY_TOGGLE)) {
            return Optional.of(new Verdict.Disabled());
        }

        double tax = taxCalculator.calculate(sender, amount);
        double totalCost = amount + tax;

        if (balances.evaluateWithdraw(sender.getUniqueId(), totalCost) instanceof BalanceLedger.Verdict.BelowMin belowMin) {
            return Optional.of(new Verdict.InsufficientFunds(belowMin.min() - belowMin.resultingBalance()));
        }

        if (balances.evaluateDeposit(targetUuid, amount) instanceof BalanceLedger.Verdict.AboveMax aboveMax) {
            return Optional.of(new Verdict.ExceedsRecipientMax(aboveMax.max()));
        }

        return Optional.empty();
    }

    public Verdict pay(Player sender, UUID targetUuid, double amount) {
        Optional<Verdict> blocked = preview(sender, targetUuid, amount);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        double tax = taxCalculator.calculate(sender, amount);

        return switch (balances.transfer(sender.getUniqueId(), targetUuid, amount, tax)) {
            case BalanceLedger.TransferResult.SenderBelowMin(double missing) ->
                    new Verdict.InsufficientFunds(missing);
            case BalanceLedger.TransferResult.ReceiverAboveMax(double limit) ->
                    new Verdict.ExceedsRecipientMax(limit);
            case BalanceLedger.TransferResult.Ok(double paid, double charged) -> {
                boolean recipientOnline = Bukkit.getPlayer(targetUuid) != null;
                if (!recipientOnline) {
                    players.addPendingPayment(targetUuid, paid);
                }
                yield new Verdict.Sent(paid, charged, recipientOnline);
            }
        };
    }

    public void notifyIfAfk(Player sender, UUID targetUuid, Verdict verdict) {
        if (verdict instanceof Verdict.Sent) {
            afk.notifyIfAfk(sender, targetUuid, ExtrasConfig.AfkNotifyAction.PAYMENTS);
        }
    }

    public double tax(Permissible sender, double amount) {
        return taxCalculator.calculate(sender, amount);
    }

    // Shared verdict -> message dispatch

    public static void respond(Messenger messenger, ConfigManager configs, AmountFormatter formatter,
                               Player sender, UUID targetUuid, Verdict verdict, PlayerService players, Runnable onSent) {
        switch (verdict) {
            case Verdict.Sent(double amount, double tax, boolean recipientOnline) -> {
                onSent.run();

                Map<String, String> senderPlaceholders = new LinkedHashMap<>();
                players.formatDisplayName(senderPlaceholders, "target", targetUuid);
                formatter.formatInto(senderPlaceholders, "amount", amount);
                formatter.formatOptionalInto(senderPlaceholders, "tax", tax, "placeholders.empty.tax");
                messenger.send(sender, configs.lang().get("economy.pay.sent"), senderPlaceholders);

                if (recipientOnline) {
                    Player recipient = Bukkit.getPlayer(targetUuid);
                    if (recipient != null) {
                        Map<String, String> receiverPlaceholders = new LinkedHashMap<>();
                        formatter.formatInto(receiverPlaceholders, "amount", amount);
                        messenger.send(recipient, configs.lang().get("economy.pay.received"), receiverPlaceholders);
                    }
                }
            }
            case Verdict.Self() ->
                    messenger.send(sender, configs.lang().get("economy.pay.errors.self"));
            case Verdict.Blocked() -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "player", targetUuid);
                messenger.send(sender, configs.lang().get("extras.block.errors.blocked-by"), placeholders);
            }
            case Verdict.Disabled() -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "target", targetUuid);
                messenger.send(sender, configs.lang().get("economy.pay.errors.disabled"), placeholders);
            }
            case Verdict.BelowMin(double min) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                formatter.formatInto(placeholders, "min", min);
                messenger.send(sender, configs.lang().get("economy.pay.errors.below-min"), placeholders);
            }
            case Verdict.AboveMax(double max) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                formatter.formatInto(placeholders, "max", max);
                messenger.send(sender, configs.lang().get("economy.pay.errors.above-max"), placeholders);
            }
            case Verdict.InsufficientFunds(double missing) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                formatter.formatInto(placeholders, "amount", missing);
                messenger.send(sender, configs.lang().get("economy.pay.errors.insufficient"), placeholders);
            }
            case Verdict.ExceedsRecipientMax(double limit) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "target", targetUuid);
                formatter.formatInto(placeholders, "limit", limit);
                messenger.send(sender, configs.lang().get("economy.pay.errors.exceed-max"), placeholders);
            }
        }
    }
}