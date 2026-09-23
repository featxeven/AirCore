package com.ftxeven.aircore.module.economy.balance;

import com.ftxeven.aircore.database.cache.PlayerCache;
import com.ftxeven.aircore.database.cache.TtlCache;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.permission.OfflinePermissions;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public final class BalanceLedger {

    public sealed interface Verdict {
        record Success(double amount, double resultingBalance) implements Verdict {}
        record BelowMin(double min, double resultingBalance) implements Verdict {}
        record AboveMax(double max, double resultingBalance) implements Verdict {}
    }

    public sealed interface TransferResult {
        record Ok(double amount, double tax) implements TransferResult {}
        record SenderBelowMin(double missing) implements TransferResult {}
        record ReceiverAboveMax(double limit) implements TransferResult {}
    }

    private record Bounds(double min, double max, boolean allowNegative, boolean allowOverMax) {

        Verdict check(double amount, double resulting) {
            if (min != -1 && resulting < min && !allowNegative) {
                return new Verdict.BelowMin(min, resulting);
            }
            if (max != -1 && resulting > max && !allowOverMax) {
                return new Verdict.AboveMax(max, resulting);
            }
            return new Verdict.Success(amount, resulting);
        }
    }

    private record BypassFlags(boolean negativeBalance, boolean maxBalance) {
        static final BypassFlags NONE = new BypassFlags(false, false);
    }

    private final Supplier<EconomyConfig> configs;
    private final PlayerService players;
    private final TtlCache<UUID, BypassFlags> bypassCache = new TtlCache<>(Duration.ofMinutes(5));

    public BalanceLedger(Supplier<EconomyConfig> configs, PlayerService players) {
        this.configs = configs;
        this.players = players;
    }

    public double balance(UUID owner) {
        return players.find(owner).map(PlayerProfile::balance).orElse(0.0);
    }

    // Bypass-flag resolution

    public CompletableFuture<Void> prepareBypassFlags(UUID owner) {
        if (bypassCache.get(owner).isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        Player online = Bukkit.getPlayer(owner);
        if (online != null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            boolean scheduled = Scheduler.runEntity(online, () -> {
                bypassCache.put(owner, new BypassFlags(
                        online.hasPermission(Permissions.Bypass.NEGATIVE_BALANCE),
                        online.hasPermission(Permissions.Bypass.MAX_BALANCE)));
                future.complete(null);
            }).isPresent();
            if (!scheduled) {
                future.complete(null); // retired between the check and scheduling; falls through the offline path next time
            }
            return future;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        Scheduler.runAsync(() -> {
            try {
                bypassCache.put(owner, new BypassFlags(
                        OfflinePermissions.hasOffline(owner, Permissions.Bypass.NEGATIVE_BALANCE),
                        OfflinePermissions.hasOffline(owner, Permissions.Bypass.MAX_BALANCE)));
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    /** Prepares several owners at once - used ahead of a transfer that touches two accounts. */
    public CompletableFuture<Void> prepare(UUID... owners) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[owners.length];
        for (int i = 0; i < owners.length; i++) {
            futures[i] = prepareBypassFlags(owners[i]);
        }
        return CompletableFuture.allOf(futures);
    }

    private BypassFlags bypassFlags(UUID owner) {
        Optional<BypassFlags> cached = bypassCache.get(owner);
        if (cached.isPresent()) {
            return cached.get();
        }
        prepareBypassFlags(owner); // self-heals the cache for next time
        return BypassFlags.NONE;
    }

    // Pure checks

    public Verdict evaluateWithdraw(UUID owner, double amount) {
        return bounds(owner).check(amount, balance(owner) - amount);
    }

    public Verdict evaluateDeposit(UUID owner, double amount) {
        return bounds(owner).check(amount, balance(owner) + amount);
    }

    // Mutations

    public Verdict withdraw(UUID owner, double amount) {
        return applyDelta(owner, -amount, amount);
    }

    public Verdict deposit(UUID owner, double amount) {
        return applyDelta(owner, amount, amount);
    }

    private Verdict applyDelta(UUID owner, double delta, double reportedAmount) {
        Bounds bounds = bounds(owner);

        Function<PlayerProfile, PlayerCache.Change<Verdict>> change = profile -> {
            double resulting = profile.balance() + delta;
            Verdict result = bounds.check(reportedAmount, resulting);
            return result instanceof Verdict.Success
                    ? PlayerCache.Change.of(profile.withBalance(resulting), result)
                    : PlayerCache.Change.unchanged(result);
        };

        Optional<Verdict> applied = players.computeProfile(owner, change);
        if (applied.isEmpty() && players.find(owner).isPresent()) {
            applied = players.computeProfile(owner, change);
        }
        if (applied.isEmpty()) {
            // no such account
            return new Verdict.BelowMin(bounds.min(), 0.0);
        }

        Verdict verdict = applied.get();
        if (verdict instanceof Verdict.Success) {
            players.persistBalanceDelta(owner, delta);
        }
        return verdict;
    }

    public Verdict set(UUID owner, double amount) {
        Bounds bounds = bounds(owner);
        Verdict verdict = bounds.check(amount, amount);
        if (verdict instanceof Verdict.Success) {
            players.updateBalance(owner, amount);
        }
        return verdict;
    }

    public double reset(UUID owner) {
        double defaultBalance = configs.get().balance().defaultBalance();
        players.updateBalance(owner, defaultBalance);
        return defaultBalance;
    }

    public TransferResult transfer(UUID from, UUID to, double amount, double tax) {
        double cost = amount + tax;

        Verdict taken = withdraw(from, cost);
        if (taken instanceof Verdict.BelowMin belowMin) {
            return new TransferResult.SenderBelowMin(belowMin.min() - belowMin.resultingBalance());
        }

        Verdict given = deposit(to, amount);
        if (given instanceof Verdict.AboveMax aboveMax) {
            refund(from, cost);
            return new TransferResult.ReceiverAboveMax(aboveMax.max());
        }
        return new TransferResult.Ok(amount, tax);
    }

    private void refund(UUID owner, double amount) {
        players.computeProfile(owner, profile ->
                PlayerCache.Change.of(profile.withBalance(profile.balance() + amount), Boolean.TRUE));
        players.persistBalanceDelta(owner, amount);
    }

    // Server-wide bulk operations

    public Verdict checkServerWideSet(double amount) {
        EconomyConfig.Balance balance = configs.get().balance();
        return new Bounds(balance.minBalance(), balance.maxBalance(), false, false).check(amount, amount);
    }

    public int depositAllServerWide(double amount) {
        EconomyConfig.Balance bounds = configs.get().balance();
        return players.addBalanceToAllPersisted(amount, bounds.minBalance(), bounds.maxBalance());
    }

    public int withdrawAllServerWide(double amount) {
        return depositAllServerWide(-amount);
    }

    public int setAllServerWide(double amount) {
        return players.setBalanceForAllPersisted(amount);
    }

    public int resetAllServerWide() {
        return players.resetBalanceForAllPersisted(configs.get().balance().defaultBalance());
    }

    private Bounds bounds(UUID owner) {
        EconomyConfig.Balance balance = configs.get().balance();
        BypassFlags flags = bypassFlags(owner);
        return new Bounds(balance.minBalance(), balance.maxBalance(), flags.negativeBalance(), flags.maxBalance());
    }
}