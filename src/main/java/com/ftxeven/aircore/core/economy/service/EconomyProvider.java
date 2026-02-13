package com.ftxeven.aircore.core.economy.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

public final class EconomyProvider implements Economy {

    private final AirCore plugin;
    private final EconomyManager manager;

    public EconomyProvider(AirCore plugin, EconomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "AirCoreEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return plugin.config().economyAllowDecimals() ? 2 : 0;
    }

    @Override
    public String format(double amount) {
        String formatted = manager.formats().formatAmount(amount);
        return "$" + formatted;
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    // Account checks
    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player.hasPlayedBefore() || player.isOnline();
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    // Balance
    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        UUID id = player.getUniqueId();
        return manager.balances().getBalance(id);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    // Has
    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return manager.balances().getBalance(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // Withdraw
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        UUID id = player.getUniqueId();
        EconomyManager.Result res = manager.transactions().withdraw(id, amount);
        if (res.type() == EconomyManager.ResultType.SUCCESS) {
            return new EconomyResponse(amount, res.balance(), EconomyResponse.ResponseType.SUCCESS, null);
        }
        String msg = switch (res.type()) {
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
            case MIN_LIMIT -> "Below minimum balance";
            case MAX_LIMIT -> "Above maximum balance";
            case INVALID -> "Invalid withdrawal";
            default -> "Failure";
        };
        return new EconomyResponse(0, res.balance(), EconomyResponse.ResponseType.FAILURE, msg);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    // Deposit
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        UUID id = player.getUniqueId();
        EconomyManager.Result res = manager.transactions().deposit(id, amount);
        if (res.type() == EconomyManager.ResultType.SUCCESS) {
            return new EconomyResponse(amount, res.balance(), EconomyResponse.ResponseType.SUCCESS, null);
        }
        String msg = switch (res.type()) {
            case MIN_LIMIT -> "Below minimum balance";
            case MAX_LIMIT -> "Above maximum balance";
            case INVALID -> "Invalid deposit";
            default -> "Failure";
        };
        return new EconomyResponse(0, res.balance(), EconomyResponse.ResponseType.FAILURE, msg);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // Create account
    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        manager.transactions().setBalance(player.getUniqueId(), plugin.config().economyDefaultBalance());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        manager.transactions().setBalance(player.getUniqueId(), plugin.config().economyDefaultBalance());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // Bank methods not supported
    @Override
    public EconomyResponse createBank(String name, String player) {
        return notSupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank support not available");
    }
}