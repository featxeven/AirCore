package com.ftxeven.aircore.command.player.economy;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.RequestAction;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.pay.PayHandler;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public final class PayCommand extends BaseCommand {

    private static final String KEY = "pay";
    private static final String CONFIRMATION_TYPE = "pay";

    private final Supplier<EconomyModule> economyModule;
    private final ConfirmationFlow confirmations;

    public PayCommand(Context ctx, Supplier<EconomyModule> economyModule, PluginGuiManager guis) {
        super(ctx, KEY);
        this.economyModule = economyModule;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs() { return 2; }

    @Override
    public int maxArgs() { return 2; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String targetToken = args[0];

        resolveTarget(sender, targetToken, target ->
                withAmount(sender, args, amount -> proceed(player, target.uuid(), amount, args)));
    }

    private void withAmount(CommandSender sender, String[] args, DoubleConsumer onValid) {
        OptionalDouble parsedAmount = economyModule.get().formatter().parse(args[1]);
        if (parsedAmount.isEmpty()) {
            messenger().send(sender, configs().lang().get("errors.general.invalid-amount"));
            return;
        }
        onValid.accept(parsedAmount.getAsDouble());
    }

    // Confirmation flow

    private void proceed(Player sender, UUID targetUuid, double amount, String[] args) {
        Scheduler.continueOn(sender, economyModule.get().balances().prepare(sender.getUniqueId(), targetUuid), (ignored, error) -> {
            if (!sender.isOnline()) {
                return;
            }
            if (error != null) {
                messenger().send(sender, configs().lang().get("errors.database"));
                return;
            }
            proceedPrepared(sender, targetUuid, amount, args);
        });
    }

    private void proceedPrepared(Player sender, UUID targetUuid, double amount, String[] args) {
        Map<String, String> placeholders = confirmationPlaceholders(sender, targetUuid, amount);
        confirmations.gated(ctx, sender, CONFIRMATION_TYPE, new PendingPayment(targetUuid, amount),
                economyModule.get().payConfirmationRequired(sender),
                () -> previewBlocked(sender, targetUuid, amount, args),
                config().gui("confirm", args),
                Map.of(GuiSession.ATTR_TARGET, targetUuid, RequestAction.ATTR_AMOUNT, amount),
                placeholders,
                GuiFlags.forPay(sender, economyModule.get(), targetUuid, amount),
                configs().economy().pay().confirmationTimeout(),
                "economy.pay.confirmation.request", "economy.pay.confirmation.expired",
                () -> applyPayment(sender, targetUuid, amount, args));
    }

    private Optional<Runnable> previewBlocked(Player sender, UUID targetUuid, double amount, String[] args) {
        return economyModule.get().pay().preview(sender, targetUuid, amount)
                .map(verdict -> (Runnable) () -> PayHandler.respond(messenger(), configs(), economyModule.get().formatter(),
                        sender, targetUuid, verdict, services().players(), () -> completeCooldown(sender, args)));
    }

    private Map<String, String> confirmationPlaceholders(Player player, UUID targetUuid, double amount) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", targetUuid);
        EconomyModule economy = economyModule.get();
        economy.formatter().formatInto(placeholders, "amount", amount);
        double tax = economy.pay().tax(player, amount);
        economy.formatter().formatOptionalInto(placeholders, "tax", tax, "placeholders.empty.tax");
        return placeholders;
    }

    // Applying

    private void applyPayment(Player sender, UUID targetUuid, double amount, String[] args) {
        PayHandler pay = economyModule.get().pay();
        PayHandler.Verdict verdict = pay.pay(sender, targetUuid, amount);
        PayHandler.respond(messenger(), configs(), economyModule.get().formatter(), sender, targetUuid, verdict,
                services().players(), () -> completeCooldown(sender, args));
        pay.notifyIfAfk(sender, targetUuid, verdict);
    }

    private record PendingPayment(UUID targetUuid, double amount) {}
}