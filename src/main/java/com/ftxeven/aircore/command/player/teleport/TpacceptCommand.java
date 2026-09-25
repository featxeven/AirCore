package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.module.teleport.TeleportModule.AcceptOutcome;
import com.ftxeven.aircore.module.teleport.request.TeleportRequestHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TpacceptCommand extends BaseCommand {

    private static final String KEY = "tpaccept";
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;

    public TpacceptCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 0; }

    @Override
    public int maxArgs(CommandSender sender) { return 1; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        if (args.length > 0 && selectors().isAll(args[0])) {
            if (!checkPermission(player, ALL_PERMISSION)) {
                return;
            }
            acceptAll(player, args);
            return;
        }

        if (args.length > 0) {
            Optional<Player> from = requireFound(resolver().onlinePlayer(args[0]), player, args[0]);
            if (from.isEmpty()) {
                return;
            }
            if (acceptOne(player, from.get().getUniqueId())) {
                completeCooldown(player, args);
            }
            return;
        }

        Optional<TeleportRequestHandler.PendingRequest> mostRecent =
                teleport.get().requests().mostRecentIncoming(player.getUniqueId());
        if (mostRecent.isEmpty()) {
            messenger().send(player, configs().lang().get("teleport.tpa.errors.no-requests"));
            return;
        }
        if (acceptOne(player, mostRecent.get().sender())) {
            completeCooldown(player, args);
        }
    }

    private boolean acceptOne(Player player, UUID senderUuid) {
        return acceptOne(player, senderUuid, true);
    }

    private boolean acceptOne(Player player, UUID senderUuid, boolean announceToAcceptor) {
        Optional<TeleportRequestHandler.PendingRequest> pending = teleport.get().requests().get(player.getUniqueId(), senderUuid);
        if (pending.isEmpty()) {
            sendNoRequestFrom(player, senderUuid);
            return false;
        }

        TeleportRequestHandler.PendingRequest request = pending.get();
        AcceptOutcome outcome = teleport.get()
                .acceptRequest(player.getUniqueId(), senderUuid, session(request.travellerUuid(), request.anchorUuid()));

        return switch (outcome) {
            case AcceptOutcome.NotFound() -> {
                sendNoRequestFrom(player, senderUuid);
                yield false;
            }
            case AcceptOutcome.MoverOffline(var offlineRequest) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                services().players().formatDisplayName(placeholders, "player", offlineRequest.sender());
                messenger().send(player, configs().lang().get("teleport.tpa.errors.mover-offline"), placeholders);
                yield false;
            }
            case AcceptOutcome.Started(var acceptedRequest) -> {
                announceAccepted(player, acceptedRequest.sender(), announceToAcceptor);
                yield true;
            }
        };
    }

    private void sendNoRequestFrom(Player player, UUID senderUuid) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", senderUuid);
        messenger().send(player, configs().lang().get("teleport.tpa.errors.no-request-from"), placeholders);
    }

    private void announceAccepted(Player acceptor, UUID senderUuid, boolean announceToAcceptor) {
        if (announceToAcceptor) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "player", senderUuid);
            messenger().send(acceptor, configs().lang().get("teleport.tpa.accepted.by-you"), placeholders);
        }
        notifyTarget(acceptor, senderUuid, configs().lang().get("teleport.tpa.accepted.by-them"), Map.of());
    }

    private void acceptAll(Player player, String[] args) {
        List<TeleportRequestHandler.PendingRequest> pending = teleport.get().requests().incomingFor(player.getUniqueId());
        if (pending.isEmpty()) {
            messenger().send(player, configs().lang().get("teleport.tpa.errors.no-requests"));
            return;
        }

        completeCooldown(player, args);
        for (TeleportRequestHandler.PendingRequest request : pending) {
            acceptOne(player, request.sender(), false);
        }
        messenger().send(player, configs().lang().get("teleport.tpa.accepted.all"));
    }

    // Countdown

    private TeleportModule.TeleportSession session(UUID travellerUuid, UUID anchorUuid) {
        return TeleportMessages.acceptSession(messenger(), configs(), services(), travellerUuid, anchorUuid);
    }
}