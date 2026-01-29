package com.ftxeven.aircore.module.core.economy.service;

import com.ftxeven.aircore.AirCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BalanceService {
    private final AirCore plugin;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    public BalanceService(AirCore plugin) {
        this.plugin = plugin;
    }

    public double getBalance(UUID id) {
        return balances.computeIfAbsent(id, u -> {
            return plugin.database().records().getBalance(u);
        });
    }

    public void saveAsync(UUID id, double balance) {
        plugin.scheduler().runAsync(
                () -> plugin.database().records().setBalance(id, balance));
    }

    public void setBalance(UUID id, double balance) {
        balances.put(id, balance);
        saveAsync(id, balance);
    }

    public void setBalanceLocal(UUID id, double balance) {
        balances.put(id, balance);
    }
    public void unloadBalance(UUID id) {
        balances.remove(id);
    }
}