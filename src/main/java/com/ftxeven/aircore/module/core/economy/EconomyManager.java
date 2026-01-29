package com.ftxeven.aircore.module.core.economy;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.economy.service.BalanceService;
import com.ftxeven.aircore.module.core.economy.service.FormatService;
import com.ftxeven.aircore.module.core.economy.service.ItemWorthService;
import com.ftxeven.aircore.module.core.economy.service.TransactionService;

public final class EconomyManager {

    private final AirCore plugin;
    private BalanceService balanceService;
    private FormatService formatService;
    private TransactionService transactionService;
    private ItemWorthService itemWorthService;

    public EconomyManager(AirCore plugin) {
        this.plugin = plugin;
        constructServices();
    }

    public void reload() {
        constructServices();
    }

    private void constructServices() {
        this.formatService = new FormatService(plugin);
        this.balanceService = new BalanceService(plugin);
        this.transactionService = new TransactionService(plugin);
        this.itemWorthService = new ItemWorthService(plugin);
    }

    public BalanceService balances() {
        return balanceService;
    }

    public FormatService formats() {
        return formatService;
    }

    public TransactionService transactions() {
        return transactionService;
    }

    public ItemWorthService worth() {
        return itemWorthService;
    }

    public record Result(ResultType type, double balance) {}

    public enum ResultType {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        MIN_LIMIT,
        MAX_LIMIT,
        INVALID
    }
}