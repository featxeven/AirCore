package com.ftxeven.aircore.module.economy.vault;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.economy.balance.BalanceLedger;
import com.ftxeven.aircore.module.economy.format.AmountFormatter;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.MiniText;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VaultEconomyProvider implements Economy {

    private static final EconomyResponse NOT_IMPLEMENTED =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "AirCore does not support bank accounts.");

    private final ConfigManager configs;
    private final PlayerService players;
    private final BalanceLedger balances;
    private final AmountFormatter formatter;

    public VaultEconomyProvider(ConfigManager configs, PlayerService players, BalanceLedger balances, AmountFormatter formatter) {
        this.configs = configs;
        this.players = players;
        this.balances = balances;
        this.formatter = formatter;
    }

    // Service registration

    public void register(Plugin plugin) {
        Bukkit.getServicesManager().register(Economy.class, this, plugin, ServicePriority.Highest);
    }

    public void unregister() {
        Bukkit.getServicesManager().unregister(Economy.class, this);
    }

    @Override
    public boolean isEnabled() {
        return configs.economy().enabled();
    }

    @Override
    public String getName() {
        return "AirCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return configs.economy().general().allowDecimals() ? 2 : 0;
    }

    @Override
    public String format(double amount) {
        return MiniText.plain(formatter.format(amount));
    }

    @Override
    public String currencyNamePlural() {
        return "";
    }

    @Override
    public String currencyNameSingular() {
        return "";
    }

    // Accounts

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return players.find(player.getUniqueId()).isPresent();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // Balance

    @Override
    public double getBalance(OfflinePlayer player) {
        return balances.balance(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // Withdraw / deposit

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        EconomyResponse rejected = reject(player, amount, "withdraw");
        return rejected != null ? rejected : toResponse(balances.withdraw(player.getUniqueId(), amount), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        EconomyResponse rejected = reject(player, amount, "deposit");
        return rejected != null ? rejected : toResponse(balances.deposit(player.getUniqueId(), amount), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    private @Nullable EconomyResponse reject(OfflinePlayer player, double amount, String action) {
        if (!Double.isFinite(amount) || amount < 0) {
            return new EconomyResponse(0, balances.balance(player.getUniqueId()), EconomyResponse.ResponseType.FAILURE,
                    "Cannot " + action + " a negative or invalid amount");
        }
        if (!hasAccount(player)) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        }
        return null;
    }

    private EconomyResponse toResponse(BalanceLedger.Verdict verdict, double requestedAmount) {
        return switch (verdict) {
            case BalanceLedger.Verdict.Success success ->
                    new EconomyResponse(requestedAmount, success.resultingBalance(), EconomyResponse.ResponseType.SUCCESS, null);
            case BalanceLedger.Verdict.BelowMin belowMin ->
                    new EconomyResponse(0, belowMin.resultingBalance(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            case BalanceLedger.Verdict.AboveMax aboveMax ->
                    new EconomyResponse(0, aboveMax.resultingBalance(), EconomyResponse.ResponseType.FAILURE, "This would exceed the maximum balance allowed");
        };
    }

    // Bank accounts - not supported

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) { return NOT_IMPLEMENTED; }
    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse deleteBank(String name) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse bankBalance(String name) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse bankHas(String name, double amount) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse bankWithdraw(String name, double amount) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse bankDeposit(String name, double amount) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return NOT_IMPLEMENTED; }
    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) { return NOT_IMPLEMENTED; }
    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) { return NOT_IMPLEMENTED; }
    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) { return NOT_IMPLEMENTED; }
    @Override
    public List<String> getBanks() { return List.of(); }
}