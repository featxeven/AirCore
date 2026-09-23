package com.ftxeven.aircore.command.player.economy;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.balance.BalanceLedger;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EconomyCommand extends AbstractCommand {

    private static final String KEY = "economy";

    private static final String GIVE = "give";
    private static final String TAKE = "take";
    private static final String SET = "set";
    private static final String RESET = "reset";

    private final Supplier<EconomyModule> economyModule;

    public EconomyCommand(Context ctx, Supplier<EconomyModule> economyModule) {
        super(ctx, KEY);
        this.economyModule = economyModule;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 2; }

    @Override
    public int maxArgs(CommandSender sender, String[] args) {
        boolean isReset = args.length > 0 && resolveAction(args[0]).filter(RESET::equals).isPresent();
        return isReset ? 2 : 3;
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> resolved = resolveAction(args[0]);
        if (resolved.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        String action = resolved.get();
        String actionKey = KEY + "." + action;

        if (!checkPermission(sender, Permissions.Command.of(actionKey))) return;

        boolean needsAmount = !action.equals(RESET);
        if (needsAmount && args.length < 3) {
            sendUsageError(sender, label, subLabel);
            return;
        }

        String targetToken = args[1];

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, Permissions.Command.all(actionKey))) return;
            withAmount(sender, action, needsAmount, args, amount -> applyToOnline(sender, action, amount, args));
            return;
        }
        if (selectors().isServer(targetToken)) {
            if (!checkPermission(sender, Permissions.Command.server(actionKey))) return;
            withAmount(sender, action, needsAmount, args, amount -> applyToServer(sender, action, amount, args));
            return;
        }

        resolveTarget(sender, targetToken, target ->
                withAmount(sender, action, needsAmount, args, amount -> applySingle(sender, target.uuid(), action, amount, args)));
    }

    private void withAmount(CommandSender sender, String action, boolean needsAmount, String[] args, Consumer<OptionalDouble> onValid) {
        if (!needsAmount) {
            onValid.accept(OptionalDouble.empty());
            return;
        }
        OptionalDouble parsed = action.equals(SET)
                ? economyModule.get().formatter().parseSigned(args[2])
                : economyModule.get().formatter().parse(args[2]);
        if (parsed.isEmpty()) {
            messenger().send(sender, configs().lang().get("errors.general.invalid-amount"));
            return;
        }
        onValid.accept(parsed);
    }

    // Single target

    private void applySingle(CommandSender sender, UUID targetUuid, String action, OptionalDouble amount, String[] args) {
        Scheduler.continueOn(sender, economyModule.get().balances().prepareBypassFlags(targetUuid), (ignored, error) -> {
            BalanceLedger balances = economyModule.get().balances();
            switch (action) {
                case GIVE -> respondToVerdict(sender, targetUuid, balances.deposit(targetUuid, amount.getAsDouble()), action, amount.getAsDouble(), args);
                case TAKE -> respondToVerdict(sender, targetUuid, balances.withdraw(targetUuid, amount.getAsDouble()), action, amount.getAsDouble(), args);
                case SET -> respondToVerdict(sender, targetUuid, balances.set(targetUuid, amount.getAsDouble()), action, amount.getAsDouble(), args);
                case RESET -> {
                    balances.reset(targetUuid);
                    completeCooldown(sender, args);
                    announce(sender, targetUuid, isSelf(sender, targetUuid),
                            "economy.reset.self", "economy.reset.other", "economy.reset.by", Map.of());
                }
            }
        });
    }

    private void respondToVerdict(CommandSender sender, UUID targetUuid, BalanceLedger.Verdict verdict, String action, double amount, String[] args) {
        switch (verdict) {
            case BalanceLedger.Verdict.Success ignored -> {
                completeCooldown(sender, args);
                Map<String, String> placeholders = new LinkedHashMap<>();
                economyModule.get().formatter().formatInto(placeholders, "amount", amount);
                announce(sender, targetUuid, isSelf(sender, targetUuid),
                        "economy." + action + ".self",
                        "economy." + action + ".other",
                        "economy." + action + ".by",
                        placeholders);
            }
            case BalanceLedger.Verdict.BelowMin belowMin -> sendBoundsError(sender, belowMin);
            case BalanceLedger.Verdict.AboveMax aboveMax -> sendBoundsError(sender, aboveMax);
        }
    }

    private void sendBoundsError(CommandSender sender, BalanceLedger.Verdict verdict) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        switch (verdict) {
            case BalanceLedger.Verdict.BelowMin belowMin -> {
                economyModule.get().formatter().formatInto(placeholders, "min", belowMin.min());
                messenger().send(sender, configs().lang().get("economy.errors.below-min"), placeholders);
            }
            case BalanceLedger.Verdict.AboveMax aboveMax -> {
                economyModule.get().formatter().formatInto(placeholders, "max", aboveMax.max());
                messenger().send(sender, configs().lang().get("economy.errors.above-max"), placeholders);
            }
            case BalanceLedger.Verdict.Success ignored -> { }
        }
    }

    // Bulk: online players

    private void applyToOnline(CommandSender sender, String action, OptionalDouble amount, String[] args) {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        BalanceLedger balances = economyModule.get().balances();
        CompletableFuture<?>[] prepared = online.stream()
                .map(p -> balances.prepareBypassFlags(p.getUniqueId()))
                .toArray(CompletableFuture[]::new);

        Scheduler.continueOn(sender, CompletableFuture.allOf(prepared), (ignored, error) -> {
            for (Player p : online) {
                applyBulk(sender, balances, p.getUniqueId(), action, amount);
            }
            completeCooldown(sender, args);
            announceBulkResult(sender, action, "online", amount);
        });
    }

    private void applyBulk(CommandSender sender, BalanceLedger balances, UUID uuid, String action, OptionalDouble amount) {
        switch (action) {
            case GIVE -> notifyIfAffected(sender, uuid, action, amount.getAsDouble(), balances.deposit(uuid, amount.getAsDouble()));
            case TAKE -> notifyIfAffected(sender, uuid, action, amount.getAsDouble(), balances.withdraw(uuid, amount.getAsDouble()));
            case SET -> notifyIfAffected(sender, uuid, action, amount.getAsDouble(), balances.set(uuid, amount.getAsDouble()));
            case RESET -> {
                balances.reset(uuid);
                notifyTarget(sender, uuid, configs().lang().get("economy.reset.by"), Map.of());
            }
        }
    }

    private void notifyIfAffected(CommandSender sender, UUID uuid, String action, double amount, BalanceLedger.Verdict verdict) {
        if (!(verdict instanceof BalanceLedger.Verdict.Success)) {
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        economyModule.get().formatter().formatInto(placeholders, "amount", amount);
        notifyTarget(sender, uuid, configs().lang().get("economy." + action + ".by"), placeholders);
    }

    // Bulk: everyone stored (@server)

    private void applyToServer(CommandSender sender, String action, OptionalDouble amount, String[] args) {
        BalanceLedger balances = economyModule.get().balances();

        if (action.equals(SET)) {
            BalanceLedger.Verdict bounds = balances.checkServerWideSet(amount.getAsDouble());
            if (!(bounds instanceof BalanceLedger.Verdict.Success)) {
                sendBoundsError(sender, bounds);
                return;
            }
        }

        Scheduler.runAsync(() -> {
            try {
                switch (action) {
                    case GIVE -> balances.depositAllServerWide(amount.getAsDouble());
                    case TAKE -> balances.withdrawAllServerWide(amount.getAsDouble());
                    case SET -> balances.setAllServerWide(amount.getAsDouble());
                    case RESET -> balances.resetAllServerWide();
                    default -> { }
                }
            } catch (RuntimeException e) {
                Scheduler.runGlobal(() -> messenger().send(sender, configs().lang().get("errors.database")));
                throw e; // still surfaces the stack trace in the console
            }

            Scheduler.runGlobal(() -> {
                completeCooldown(sender, args);
                announceBulkResult(sender, action, "all", amount);
                notifyOnlineOfBulkChange(sender, action, amount);
            });
        });
    }

    private void notifyOnlineOfBulkChange(CommandSender sender, String action, OptionalDouble amount) {
        String langKey = action.equals(RESET) ? "economy.reset.by" : "economy." + action + ".by";
        Map<String, String> placeholders = new LinkedHashMap<>();
        amount.ifPresent(value -> economyModule.get().formatter().formatInto(placeholders, "amount", value));
        UUID senderUuid = sender instanceof Player player ? player.getUniqueId() : null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(senderUuid)) {
                continue;
            }
            notifyTarget(sender, online.getUniqueId(), configs().lang().get(langKey), placeholders);
        }
    }

    private void announceBulkResult(CommandSender sender, String action, String scopeKey, OptionalDouble amount) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        amount.ifPresent(value -> economyModule.get().formatter().formatInto(placeholders, "amount", value));
        messenger().send(sender, configs().lang().get("economy." + action + "." + scopeKey), placeholders);
    }
}