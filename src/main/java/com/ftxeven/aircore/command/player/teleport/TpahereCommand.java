package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class TpahereCommand extends AbstractCommand {

    private static final String KEY = "tpahere";
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);
    private static final String CONFIRMATION_TYPE = "tpahere";
    private static final String CONFIRMATION_TYPE_ALL = "tpahere-all";
    private static final String SENT_TO_SELF = "teleport.tpa.here-sent-to";
    private static final String RECEIVED_BY_TARGET = "teleport.tpa.here-received-from";

    private final Supplier<TeleportModule> teleport;
    private final ConfirmationFlow confirmations;

    public TpahereCommand(Context ctx, Supplier<TeleportModule> teleport, PluginGuiManager guis) {
        super(ctx, KEY);
        this.teleport = teleport;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 1; }

    @Override
    public int maxArgs(CommandSender sender) { return 1; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player anchor = (Player) sender;
        String targetToken = args[0];

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, ALL_PERMISSION)) {
                return;
            }

            OptionalDouble cooldown = teleport.get().previewRequestAll(anchor);
            if (cooldown.isPresent()) {
                sendCooldownAll(anchor, cooldown.getAsDouble());
                return;
            }

            if (requireOtherPlayersOnline(sender, anchor.getUniqueId()).isEmpty()) {
                return;
            }
            proceedAll(anchor, args);
            return;
        }

        Optional<Player> target = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (target.isEmpty()) {
            return;
        }
        proceedOne(anchor, target.get(), args);
    }

    // Single target

    private void proceedOne(Player anchor, Player target, String[] args) {
        confirmations.gated(ctx, anchor, CONFIRMATION_TYPE, target.getUniqueId(),
                teleport.get().sendConfirmationRequired(anchor),
                () -> previewBlockedOne(anchor, target, args),
                config().gui("confirm", args),
                Map.of("target", target.getName()),
                requestPlaceholders(target),
                GuiFlags.builder(anchor).online(target.getUniqueId()).build(),
                configs().teleport().requests().confirmationTimeout(),
                "teleport.tpa.confirmation.here.request", "teleport.tpa.confirmation.here.expired",
                () -> sendOne(anchor, target, args));
    }

    private Optional<Runnable> previewBlockedOne(Player anchor, Player target, String[] args) {
        return teleport.get().previewRequest(anchor, target)
                .map(result -> (Runnable) () -> TeleportMessages.notifySendResult(messenger(), configs(), services(), teleport.get(),
                        anchor, target, result, SENT_TO_SELF, RECEIVED_BY_TARGET, () -> completeCooldown(anchor, args)));
    }

    private void sendOne(Player anchor, Player target, String[] args) {
        TeleportMessages.sendTeleportRequest(messenger(), configs(), services(), teleport.get(), anchor, target, TeleportType.TPAHERE,
                SENT_TO_SELF, RECEIVED_BY_TARGET, () -> completeCooldown(anchor, args));
    }

    // Everyone online

    private void proceedAll(Player anchor, String[] args) {
        String allToken = selectors().required(Selectors.ALL);
        confirmations.gated(ctx, anchor, CONFIRMATION_TYPE_ALL, Boolean.TRUE,
                teleport.get().sendConfirmationRequired(anchor),
                Optional::empty,
                config().gui("confirm-all", args),
                Map.of("target", allToken),
                Map.of(),
                GuiFlags.builder(anchor).online().build(),
                configs().teleport().requests().confirmationTimeout(),
                "teleport.tpa.confirmation.here.request-all", "teleport.tpa.confirmation.here.expired-all",
                () -> sendAll(anchor, args));
    }

    private void sendAll(Player anchor, String[] args) {
        List<Player> targets = onlineExcept(anchor.getUniqueId());
        if (targets.isEmpty()) {
            messenger().send(anchor, configs().lang().get("errors.access.no-players-online"));
            return;
        }

        completeCooldown(anchor, args);
        TeleportMessages.sendTpahereToAll(messenger(), configs(), services(), teleport.get(), anchor, targets, RECEIVED_BY_TARGET);
    }

    private void sendCooldownAll(Player anchor, double remainingSeconds) {
        Map<String, String> placeholders = Map.of("timeout",
                TimeFormatter.duration(remainingSeconds, configs().main().formatting(), configs().lang()));
        messenger().send(anchor, configs().lang().get("teleport.tpa.errors.cooldown-all"), placeholders);
    }

    // Shared

    // carries both %player% (chat confirmation/expiry messages) and %target% (confirm GUI)
    private Map<String, String> requestPlaceholders(Player target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", target.getUniqueId());
        services().players().formatDisplayName(placeholders, "target", target.getUniqueId());
        return placeholders;
    }
}