package com.ftxeven.aircore.core.economy.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;

import java.util.UUID;

public final class TransactionService {
    private final AirCore plugin;

    public TransactionService(AirCore plugin) {
        this.plugin = plugin;
    }

    public EconomyManager.Result setBalance(UUID id, double amount) {
        double min = plugin.config().economyMinBalance();
        double max = plugin.config().economyMaxBalance();

        if (min >= 0 && amount < 0) {
            return new EconomyManager.Result(EconomyManager.ResultType.INVALID,
                    plugin.economy().balances().getBalance(id));
        }
        if (min < 0 && min != -1 && amount < min) {
            return new EconomyManager.Result(EconomyManager.ResultType.MIN_LIMIT,
                    plugin.economy().balances().getBalance(id));
        }
        if (max >= 0 && amount > max) {
            return new EconomyManager.Result(EconomyManager.ResultType.MAX_LIMIT,
                    plugin.economy().balances().getBalance(id));
        }

        double newBalance = plugin.economy().formats().round(amount);
        plugin.economy().balances().setBalance(id, newBalance);
        return new EconomyManager.Result(EconomyManager.ResultType.SUCCESS, newBalance);
    }

    public EconomyManager.Result deposit(UUID id, double amount) {
        if (amount <= 0) {
            return new EconomyManager.Result(EconomyManager.ResultType.INVALID,
                    plugin.economy().balances().getBalance(id));
        }

        double current = plugin.economy().balances().getBalance(id);
        double newBalance = plugin.economy().formats().round(current + amount);

        double max = plugin.config().economyMaxBalance();
        if (max >= 0 && newBalance > max) {
            return new EconomyManager.Result(EconomyManager.ResultType.MAX_LIMIT, current);
        }

        plugin.economy().balances().setBalance(id, newBalance);
        return new EconomyManager.Result(EconomyManager.ResultType.SUCCESS, newBalance);
    }

    public EconomyManager.Result withdraw(UUID id, double amount) {
        double current = plugin.economy().balances().getBalance(id);
        if (amount <= 0) {
            return new EconomyManager.Result(EconomyManager.ResultType.INVALID, current);
        }

        double min = plugin.config().economyMinBalance();
        double allowed = (min != -1) ? current - min : amount;
        double taken = Math.min(amount, allowed);

        if (taken <= 0) {
            return new EconomyManager.Result(EconomyManager.ResultType.INSUFFICIENT_FUNDS, current);
        }

        double newBalance = plugin.economy().formats().round(current - taken);
        plugin.economy().balances().setBalance(id, newBalance);
        return new EconomyManager.Result(EconomyManager.ResultType.SUCCESS, newBalance);
    }

    public void resetBalance(UUID id) {
        double newBalance = plugin.economy().formats().round(plugin.config().economyDefaultBalance());
        plugin.economy().balances().setBalance(id, newBalance);
    }
}