package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.pay.PayHandler;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Supplier;

public final class RequestAction implements ConfirmableAction {

    public static final String ATTR_AMOUNT = "amount"; // read by PayCommand when opening its confirm GUI

    private static final String VERB = "request";
    private static final String TPAHERE_ALL_PERMISSION = Permissions.Command.all("tpahere");

    private static final String TPA_SENT_TO_SELF = "teleport.tpa.sent-to";
    private static final String TPA_RECEIVED_BY_TARGET = "teleport.tpa.received-from";
    private static final String TPAHERE_SENT_TO_SELF = "teleport.tpa.here-sent-to";
    private static final String TPAHERE_RECEIVED_BY_TARGET = "teleport.tpa.here-received-from";

    private final ConfigManager configs;
    private final Supplier<TeleportModule> teleport;
    private final Supplier<EconomyModule> economy;
    private final PlayerTargetResolver resolver;
    private final Selectors selectors;
    private final ServiceManager services;

    public RequestAction(ConfigManager configs, Supplier<TeleportModule> teleport, Supplier<EconomyModule> economy,
                         PlayerTargetResolver resolver, Selectors selectors, ServiceManager services) {
        this.configs = configs;
        this.teleport = teleport;
        this.economy = economy;
        this.resolver = resolver;
        this.selectors = selectors;
        this.services = services;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "tpa" -> requestTpa(context, args);
            case "tpahere" -> requestTpahere(context, args);
            case "pay" -> requestPay(context, args);
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
            case "tpa" -> decideTpa(context, args);
            case "tpahere" -> decideTpahere(context, args);
            case "pay" -> decidePay(context, args);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield Decision.immediate();
            }
        };
    }

    // tpa

    private void requestTpa(ActionContext context, String args) {
        Player target = GuiActions.resolveOnlineTarget(context, configs, resolver, GuiActions.resolveArgOrAttribute(context, args, "target"));
        if (target != null) {
            TeleportMessages.sendTeleportRequest(context.messenger(), configs, services, teleport.get(), context.viewer(), target, TeleportType.TPA,
                    TPA_SENT_TO_SELF, TPA_RECEIVED_BY_TARGET, () -> { });
        }
    }

    private Decision decideTpa(ActionContext context, Map<String, String> args) {
        Player sender = context.viewer();
        String token = args.get("target");
        if (token == null || token.isBlank()) {
            return Decision.immediate();
        }

        Player target = resolver.onlinePlayer(token).orElse(null);
        if (target == null) {
            return Decision.immediate();
        }
        if (!teleport.get().sendConfirmationRequired(sender)) {
            return Decision.immediate();
        }
        if (teleport.get().previewRequest(sender, target).isPresent()) {
            return Decision.immediate();
        }
        return Decision.needsConfirmation(targetPlaceholders(target), GuiFlags.builder(sender).online(target.getUniqueId()).build());
    }

    // tpahere

    private void requestTpahere(ActionContext context, String args) {
        String token = GuiActions.resolveArgOrAttribute(context, args, "target");
        Player anchor = context.viewer();

        if (selectors.isAll(token)) {
            if (!anchor.hasPermission(TPAHERE_ALL_PERMISSION)) {
                context.messenger().send(anchor, configs.lang().get("errors.access.no-permission"), Map.of("permission", TPAHERE_ALL_PERMISSION));
                return;
            }
            sendTpahereToAll(context, anchor);
            return;
        }

        Player target = GuiActions.resolveOnlineTarget(context, configs, resolver, token);
        if (target != null) {
            TeleportMessages.sendTeleportRequest(context.messenger(), configs, services, teleport.get(), anchor, target, TeleportType.TPAHERE,
                    TPAHERE_SENT_TO_SELF, TPAHERE_RECEIVED_BY_TARGET, () -> { });
        }
    }

    private Decision decideTpahere(ActionContext context, Map<String, String> args) {
        Player anchor = context.viewer();
        String token = args.get("target");
        if (token == null || token.isBlank()) {
            return Decision.immediate();
        }

        if (selectors.isAll(token)) {
            return decideTpahereAll(anchor);
        }

        Player target = resolver.onlinePlayer(token).orElse(null);
        if (target == null) {
            return Decision.immediate();
        }
        if (!teleport.get().sendConfirmationRequired(anchor)) {
            return Decision.immediate();
        }
        if (teleport.get().previewRequest(anchor, target).isPresent()) {
            return Decision.immediate();
        }
        return Decision.needsConfirmation(targetPlaceholders(target), GuiFlags.builder(anchor).online(target.getUniqueId()).build());
    }

    private Decision decideTpahereAll(Player anchor) {
        if (!anchor.hasPermission(TPAHERE_ALL_PERMISSION)) {
            return Decision.denied(new Eligibility.Denied("errors.access.no-permission", Map.of("permission", TPAHERE_ALL_PERMISSION)));
        }
        OptionalDouble cooldown = teleport.get().previewRequestAll(anchor);
        if (cooldown.isPresent()) {
            Map<String, String> placeholders = Map.of("timeout",
                    TimeFormatter.duration(cooldown.getAsDouble(), configs.main().formatting(), configs.lang()));
            return Decision.denied(new Eligibility.Denied("teleport.tpa.errors.cooldown-all", placeholders));
        }
        if (onlineExcept(anchor).isEmpty()) {
            return Decision.denied(new Eligibility.Denied("errors.access.no-players-online"));
        }
        return teleport.get().sendConfirmationRequired(anchor)
                ? Decision.needsConfirmation(Map.of(), GuiFlags.builder(anchor).online().build())
                : Decision.immediate();
    }

    private void sendTpahereToAll(ActionContext context, Player anchor) {
        List<Player> targets = onlineExcept(anchor);
        if (targets.isEmpty()) {
            context.messenger().send(anchor, configs.lang().get("errors.access.no-players-online"));
            return;
        }
        TeleportMessages.sendTpahereToAll(context.messenger(), configs, services, teleport.get(), anchor, targets, TPAHERE_RECEIVED_BY_TARGET);
    }

    private List<Player> onlineExcept(Player excluded) {
        List<Player> others = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(excluded.getUniqueId())) {
                others.add(online);
            }
        }
        return others;
    }

    private Map<String, String> targetPlaceholders(Player target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services.players().formatDisplayName(placeholders, "target", target.getUniqueId());
        return placeholders;
    }

    private Map<String, String> targetPlaceholders(UUID targetUuid) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services.players().formatDisplayName(placeholders, "target", targetUuid);
        return placeholders;
    }

    // pay

    private void requestPay(ActionContext context, String args) {
        UUID targetUuid = resolvePayTargetUuid(context, GuiActions.resolveArgOrAttribute(context, args, "target"));
        if (targetUuid == null) {
            return;
        }
        OptionalDouble amount = parsePayAmount(context, GuiActions.resolveArgOrAttribute(context, args, "amount"));
        if (amount.isEmpty()) {
            return;
        }

        Player sender = context.viewer();
        PayHandler pay = economy.get().pay();
        PayHandler.Verdict verdict = pay.pay(sender, targetUuid, amount.getAsDouble());
        PayHandler.respond(context.messenger(), configs, economy.get().formatter(), sender, targetUuid, verdict, services.players(), () -> { });
        pay.notifyIfAfk(sender, targetUuid, verdict);
    }

    private Decision decidePay(ActionContext context, Map<String, String> args) {
        String amountToken = args.get("amount");
        OptionalDouble amount = amountToken != null ? economy.get().formatter().parse(amountToken) : OptionalDouble.empty();
        if (amount.isEmpty()) {
            return Decision.denied(new Eligibility.Denied("errors.general.invalid-amount"));
        }

        UUID targetUuid = resolvePayTargetUuid(context, args.get("target"));
        if (targetUuid == null) {
            return Decision.immediate();
        }

        Player sender = context.viewer();
        double resolvedAmount = amount.getAsDouble();
        if (economy.get().pay().preview(sender, targetUuid, resolvedAmount).isPresent()) {
            return Decision.immediate();
        }
        if (!economy.get().payConfirmationRequired(sender)) {
            return Decision.immediate();
        }

        return Decision.needsConfirmation(
                payConfirmationPlaceholders(sender, targetUuid, resolvedAmount),
                GuiFlags.forPay(sender, economy.get(), targetUuid, resolvedAmount));
    }

    private UUID resolvePayTargetUuid(ActionContext context, String explicitToken) {
        UUID sessionTarget = context.session().target();
        if (sessionTarget != null) {
            return sessionTarget;
        }
        Player online = GuiActions.resolveOnlineTarget(context, configs, resolver, explicitToken);
        return online != null ? online.getUniqueId() : null;
    }

    private OptionalDouble parsePayAmount(ActionContext context, String token) {
        OptionalDouble parsed = token != null ? economy.get().formatter().parse(token) : OptionalDouble.empty();
        if (parsed.isEmpty()) {
            context.messenger().send(context.viewer(), configs.lang().get("errors.general.invalid-amount"));
        }
        return parsed;
    }

    private Map<String, String> payConfirmationPlaceholders(Player sender, UUID targetUuid, double amount) {
        Map<String, String> placeholders = targetPlaceholders(targetUuid);
        economy.get().formatter().formatInto(placeholders, "amount", amount);
        double tax = economy.get().pay().tax(sender, amount);
        economy.get().formatter().formatOptionalInto(placeholders, "tax", tax, "placeholders.empty.tax");
        return placeholders;
    }
}