package com.ftxeven.aircore.module.economy;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.hook.HookRegistry;
import com.ftxeven.aircore.module.economy.balance.BalanceLedger;
import com.ftxeven.aircore.module.economy.format.AmountFormatter;
import com.ftxeven.aircore.module.economy.pay.PayHandler;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.module.economy.vault.VaultEconomyProvider;
import com.ftxeven.aircore.module.economy.worth.WorthCalculator;
import com.ftxeven.aircore.module.extras.ExtrasModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EconomyModule {

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;

    private final AmountFormatter formatter;
    private final BalanceLedger balances;
    private final PayHandler pay;
    private final WorthCalculator worth;
    private final SellHandler sell;

    private VaultEconomyProvider vaultProvider;

    public EconomyModule(JavaPlugin plugin, ConfigManager configs, Messenger messenger,
                         ServiceManager services, ExtrasModule extras, HookRegistry hooks) {
        this.plugin = plugin;
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;

        this.formatter = new AmountFormatter(configs::economy, configs::lang);

        TaxCalculator taxCalculator = new TaxCalculator(configs::economy);

        this.balances = new BalanceLedger(configs::economy, services.players());
        this.pay = new PayHandler(configs::economy, services.players(), balances, extras.blocks(), extras.afk(), taxCalculator);

        this.worth = new WorthCalculator(configs::worthItems, configs::worthModifiers, hooks);
        this.sell = new SellHandler(configs::economy, worth, balances, taxCalculator);

        registerVault();
    }

    public boolean enabled() {
        return configs.economy().enabled();
    }

    public AmountFormatter formatter() { return formatter; }
    public BalanceLedger balances() { return balances; }
    public PayHandler pay() { return pay; }
    public WorthCalculator worth() { return worth; }
    public SellHandler sell() { return sell; }

    public boolean payConfirmationRequired(Player sender) {
        if (!configs.economy().pay().requireConfirmation()) {
            return false;
        }
        return services.players().peek(sender.getUniqueId())
                .map(profile -> profile.toggles().payConfirm())
                .orElse(true);
    }

    // Lifecycle

    public void reload() {
        if (enabled()) {
            registerVault();
        } else {
            unregisterVault();
        }
    }

    public void stop() {
        unregisterVault();
    }

    // Vault registration

    private void registerVault() {
        if (vaultProvider != null || !enabled()) {
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            plugin.getLogger().severe("The economy module is enabled but Vault is not installed.");
            plugin.getLogger().severe("Commands (/balance, /pay, /sell...) still work, but other plugins cannot use it.");
            plugin.getLogger().severe("Install Vault, or set 'enabled: false' in modules/economy.yml if another plugin handles the economy.");
            return;
        }

        try {
            VaultEconomyProvider provider = new VaultEconomyProvider(configs, services.players(), balances, formatter);
            provider.register(plugin);
            vaultProvider = provider;
            plugin.getLogger().info("Successfully hooked into Vault for economy support");
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().severe("Vault is installed but its economy API could not be used, the Vault hook was skipped: " + e);
        }
    }

    private void unregisterVault() {
        if (vaultProvider == null) {
            return;
        }
        vaultProvider.unregister();
        vaultProvider = null;
    }

    // Join handling

    public void handleJoin(Player player) {
        if (!enabled() || !configs.economy().pay().notifications().enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int delay = configs.economy().pay().notifications().joinDelay();
        Scheduler.runEntityLater(player, () -> notifyPendingPayment(player, uuid), delay);
    }

    private void notifyPendingPayment(Player player, UUID uuid) {
        services.players().peek(uuid).ifPresent(profile -> {
            double amount = profile.pendingPayment();
            double minAmount = configs.economy().pay().notifications().minAmount();
            if (amount <= 0 || amount < minAmount) {
                return;
            }

            Map<String, String> placeholders = new HashMap<>();
            formatter.formatInto(placeholders, "amount", amount);
            messenger.send(player, configs.lang().get("economy.pay.offline-notification"), placeholders);
            services.players().clearPendingPayment(uuid);
        });
    }
}