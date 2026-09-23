package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.gui.BaseHoldingGui;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.item.HoldingKind;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ItemAction implements ConfirmableAction {

    private static final String VERB = "item";
    private static final String DISPOSAL_COMMAND_KEY = "disposal";
    private static final String SELL_COMMAND_KEY = "sell";

    private final ConfigManager configs;
    private final ServiceManager services;
    private final Supplier<EconomyModule> economy;

    public ItemAction(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy) {
        this.configs = configs;
        this.services = services;
        this.economy = economy;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "disposal" -> disposal(context, ActionTokens.parse(args));
            case "sell" -> sell(context, ActionTokens.parse(args));
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    @Override
    public Decision decide(ActionContext context, Map<String, String> args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return Decision.immediate();
        }
        return switch (type) {
            case "disposal" -> decideDisposal(context, args);
            case "sell" -> decideSell(context, args);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield Decision.immediate();
            }
        };
    }

    // disposal

    private void disposal(ActionContext context, Map<String, String> args) {
        Player player = context.viewer();
        Scope scope = scope(args);
        int amount = disposalAmount(player, scope);

        if (amount <= 0) {
            context.messenger().send(player, configs.lang().get(emptyErrorKey(scope)));
            return;
        }

        services.holding().clear(player.getUniqueId(), HoldingKind.DISPOSAL);
        if (scope == Scope.ALL) {
            clearOwnedStorage(player);
        }

        services.commandCooldowns().complete(DISPOSAL_COMMAND_KEY, player, new String[0]);
        context.messenger().send(player, configs.lang().get("utilities.disposal.success"), Map.of("amount", String.valueOf(amount)));
        GuiActions.refreshLater(context);
    }

    private Decision decideDisposal(ActionContext context, Map<String, String> args) {
        Player player = context.viewer();
        Scope scope = scope(args);
        int amount = disposalAmount(player, scope);
        if (amount <= 0) {
            return Decision.denied(new Eligibility.Denied(emptyErrorKey(scope)));
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put(scope == Scope.ALL ? "amount_all" : "amount", String.valueOf(amount));
        return Decision.needsConfirmation(placeholders, GuiFlags.builder(player).build());
    }

    private int disposalAmount(Player player, Scope scope) {
        int held = services.holding().count(player.getUniqueId(), HoldingKind.DISPOSAL);
        return scope == Scope.ALL ? held + BaseHoldingGui.count(BaseHoldingGui.ownedStorage(player)) : held;
    }

    private void clearOwnedStorage(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            inventory.setItem(slot, null);
        }
    }

    private String emptyErrorKey(Scope scope) {
        return scope == Scope.ALL ? "utilities.disposal.errors.empty-all" : "utilities.disposal.errors.empty";
    }

    // sell

    private void sell(ActionContext context, Map<String, String> args) {
        Player player = context.viewer();
        Scope scope = scope(args);
        boolean skipInvalid = configs.economy().gui().sell().skipInvalidItems();

        ItemStack[] held = BaseHoldingGui.held(services.holding(), player, HoldingKind.SELL);
        ItemStack[] items = scope == Scope.ALL ? BaseHoldingGui.combined(held, BaseHoldingGui.ownedStorage(player)) : held;

        SellHandler sell = economy.get().sell();
        SellHandler.BatchResult result = sell.sellBatch(player, items, skipInvalid);

        if (!(result.verdict() instanceof SellHandler.Verdict.Sold sold)) {
            sellDenial(result.verdict()).send(player, configs, context.messenger());
            return;
        }

        applySale(player, scope, held.length, items, result.sold());
        services.commandCooldowns().complete(SELL_COMMAND_KEY, player, new String[0]);

        Map<String, String> placeholders = new LinkedHashMap<>();
        GuiPlaceholders.formatSellAmount(placeholders, economy.get().formatter(), "amount", "tax", sold);
        context.messenger().send(player, configs.lang().get("economy.sell.success"), placeholders);
        GuiActions.refreshLater(context);
    }

    private Decision decideSell(ActionContext context, Map<String, String> args) {
        Player player = context.viewer();
        Scope scope = scope(args);
        boolean skipInvalid = configs.economy().gui().sell().skipInvalidItems();

        ItemStack[] held = BaseHoldingGui.held(services.holding(), player, HoldingKind.SELL);
        ItemStack[] items = scope == Scope.ALL ? BaseHoldingGui.combined(held, BaseHoldingGui.ownedStorage(player)) : held;

        SellHandler sell = economy.get().sell();
        SellHandler.Verdict verdict = sell.previewBatch(player, items, skipInvalid);
        if (!(verdict instanceof SellHandler.Verdict.Sold)) {
            return Decision.denied(sellDenial(verdict));
        }

        String worthKey = scope == Scope.ALL ? "worth_all" : "worth";
        String taxKey = scope == Scope.ALL ? "tax_all" : "tax";
        Map<String, String> placeholders = new LinkedHashMap<>();
        GuiPlaceholders.formatSellAmount(placeholders, economy.get().formatter(), worthKey, taxKey, verdict);

        return Decision.needsConfirmation(placeholders, GuiFlags.forSell(player, sell, held, items, skipInvalid));
    }

    private void applySale(Player player, Scope scope, int heldLength, ItemStack[] items, boolean[] sold) {
        ItemStack[] remainingHeld = new ItemStack[heldLength];
        for (int i = 0; i < heldLength; i++) {
            remainingHeld[i] = sold[i] ? null : items[i];
        }
        services.holding().store(player.getUniqueId(), HoldingKind.SELL, remainingHeld);

        if (scope == Scope.ALL) {
            PlayerInventory inventory = player.getInventory();
            for (int i = heldLength; i < items.length; i++) {
                if (sold[i]) {
                    inventory.setItem(i - heldLength, null);
                }
            }
        }
    }

    private Eligibility.Denied sellDenial(SellHandler.Verdict verdict) {
        return switch (verdict) {
            case SellHandler.Verdict.Nothing ignored -> new Eligibility.Denied("economy.sell.errors.nothing");
            case SellHandler.Verdict.Invalid ignored -> new Eligibility.Denied("economy.sell.errors.some-invalid");
            case SellHandler.Verdict.ExceedsMax(double limit) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                economy.get().formatter().formatInto(placeholders, "limit", limit);
                yield new Eligibility.Denied("economy.sell.errors.exceed-max", placeholders);
            }
            case SellHandler.Verdict.Sold ignored -> throw new IllegalStateException("Sold is not a denial");
        };
    }

    private Scope scope(Map<String, String> args) {
        return "all".equalsIgnoreCase(args.get("scope")) ? Scope.ALL : Scope.SELECTED;
    }

    private enum Scope { SELECTED, ALL }
}