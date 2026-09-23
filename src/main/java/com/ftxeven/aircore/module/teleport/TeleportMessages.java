package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.countdown.CountdownGate;
import com.ftxeven.aircore.module.teleport.request.TeleportRequestHandler;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeleportMessages {

    private TeleportMessages() {
    }

    // Teleport sessions

    public static TeleportModule.TeleportSession session(Messenger messenger, ConfigManager configs, ServiceManager services, Player mover,
                                                         CommandSender outcomeRecipient, World destinationWorld,
                                                         @Nullable UUID destinationUuid,
                                                         String cancelledKey, Map<String, String> cancelledPlaceholders,
                                                         Runnable onSuccess) {
        return new TeleportModule.TeleportSession() {
            @Override
            public void onTick(int secondsRemaining) {
                messenger.send(mover, configs.lang().get("teleport.countdown"), Map.of(
                        "time", TimeFormatter.duration(Duration.ofSeconds(secondsRemaining), configs.main().formatting(), configs.lang())
                ));
            }

            @Override
            public void onCancel(CountdownGate.CancelReason reason) {
                if (reason instanceof CountdownGate.CancelReason.Disconnected) {
                    return; // mover just disconnected
                }
                messenger.send(mover, configs.lang().get(cancelledKey), cancelledPlaceholders);
            }

            @Override
            public void onComplete(TeleportExecutor.Outcome outcome) {
                dispatchTeleportOutcome(messenger, configs, services.players(), outcomeRecipient, destinationWorld, destinationUuid, outcome, onSuccess);
            }
        };
    }

    private static void dispatchTeleportOutcome(Messenger messenger, ConfigManager configs, PlayerService players, CommandSender recipient, World world,
                                                @Nullable UUID destinationUuid, TeleportExecutor.Outcome outcome, Runnable onSuccess) {
        switch (outcome) {
            case TeleportExecutor.Outcome.Teleported() -> onSuccess.run();
            case TeleportExecutor.Outcome.DisabledWorld() -> messenger.send(recipient,
                    configs.lang().get("teleport.errors.blocked-world"), Map.of("world", world.getName()));
            case TeleportExecutor.Outcome.UnsafeDestination() -> messenger.send(recipient,
                    configs.lang().get("teleport.errors.unsafe-destination"));
            case TeleportExecutor.Outcome.DestinationOffline() -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                if (destinationUuid != null) {
                    players.formatDisplayName(placeholders, "target", destinationUuid);
                }
                messenger.send(recipient, configs.lang().get("teleport.errors.destination-offline"), placeholders);
            }
        }
    }

    public static TeleportModule.TeleportSession acceptSession(Messenger messenger, ConfigManager configs, ServiceManager services,
                                                               UUID travellerUuid, UUID anchorUuid) {
        return new TeleportModule.TeleportSession() {
            @Override
            public void onTick(int secondsRemaining) {
                Player traveller = Bukkit.getPlayer(travellerUuid);
                if (traveller != null) {
                    messenger.send(traveller, configs.lang().get("teleport.countdown"), Map.of(
                            "time", TimeFormatter.duration(Duration.ofSeconds(secondsRemaining), configs.main().formatting(), configs.lang())));
                }
            }

            @Override
            public void onCancel(CountdownGate.CancelReason reason) {
                if (reason instanceof CountdownGate.CancelReason.Disconnected) {
                    Player anchor = Bukkit.getPlayer(anchorUuid);
                    if (anchor != null) {
                        Map<String, String> placeholders = new LinkedHashMap<>();
                        services.players().formatDisplayName(placeholders, "player", travellerUuid);
                        messenger.send(anchor, configs.lang().get("teleport.tpa.errors.mover-offline"), placeholders);
                    }
                    return;
                }
                Player traveller = Bukkit.getPlayer(travellerUuid);
                if (traveller != null) {
                    Map<String, String> placeholders = new LinkedHashMap<>();
                    services.players().formatDisplayName(placeholders, "target", anchorUuid);
                    messenger.send(traveller, configs.lang().get("teleport.tpa.teleported.cancelled"), placeholders);
                }
            }

            @Override
            public void onComplete(TeleportExecutor.Outcome outcome) {
                Player traveller = Bukkit.getPlayer(travellerUuid);
                if (traveller == null) {
                    return;
                }
                Player anchor = Bukkit.getPlayer(anchorUuid);

                switch (outcome) {
                    case TeleportExecutor.Outcome.DisabledWorld() -> {
                        String world = anchor != null ? anchor.getWorld().getName() : "";
                        messenger.send(traveller, configs.lang().get("teleport.errors.blocked-world"), Map.of("world", world));
                    }
                    case TeleportExecutor.Outcome.UnsafeDestination() ->
                            messenger.send(traveller, configs.lang().get("teleport.errors.unsafe-destination"));
                    case TeleportExecutor.Outcome.DestinationOffline() -> {
                        Map<String, String> placeholders = new LinkedHashMap<>();
                        services.players().formatDisplayName(placeholders, "target", anchorUuid);
                        messenger.send(traveller, configs.lang().get("teleport.errors.destination-offline"), placeholders);
                    }
                    case TeleportExecutor.Outcome.Teleported() -> {
                        if (anchor != null) {
                            Map<String, String> travellerPlaceholders = new LinkedHashMap<>();
                            services.players().formatDisplayName(travellerPlaceholders, "target", anchorUuid);
                            messenger.send(traveller, configs.lang().get("teleport.tpa.teleported.self"), travellerPlaceholders);

                            Map<String, String> anchorPlaceholders = new LinkedHashMap<>();
                            services.players().formatDisplayName(anchorPlaceholders, "player", travellerUuid);
                            messenger.send(anchor, configs.lang().get("teleport.tpa.teleported.other"), anchorPlaceholders);
                        }
                    }
                }
            }
        };
    }

    // Teleport requests (TPA / TPAHERE)

    public static void sendTeleportRequest(Messenger messenger, ConfigManager configs, ServiceManager services, TeleportModule teleport,
                                           Player sender, Player target, TeleportType type,
                                           String sentToSelfKey, String receivedByTargetKey, Runnable onSent) {
        TeleportRequestHandler.SendResult result = teleport.sendRequest(sender, target, type);

        if (result instanceof TeleportRequestHandler.SendResult.Sent && teleport.targetAutoAccepts(target)) {
            onSent.run();
            notifyAutoAccepted(messenger, configs, services, teleport, sender, target, true);
            return;
        }

        notifySendResult(messenger, configs, services, teleport, sender, target, result, sentToSelfKey, receivedByTargetKey, onSent);

        if (result instanceof TeleportRequestHandler.SendResult.Sent) {
            teleport.notifyIfAfk(sender, target);
        }
    }

    public static void sendTpahereToAll(Messenger messenger, ConfigManager configs, ServiceManager services, TeleportModule teleport,
                                        Player anchor, List<Player> targets, String receivedByTargetKey) {
        teleport.markRequestAllSent(anchor);

        for (Player target : targets) {
            TeleportRequestHandler.SendResult result = teleport.sendRequest(anchor, target, TeleportType.TPAHERE);
            if (!(result instanceof TeleportRequestHandler.SendResult.Sent sent)) {
                continue;
            }

            if (teleport.targetAutoAccepts(target)) {
                notifyAutoAccepted(messenger, configs, services, teleport, anchor, target, false);
                continue;
            }

            String expiresIn = TimeFormatter.expiresIn(sent.expireAfterSeconds(), configs.main().formatting(), configs.lang());
            Map<String, String> placeholders = pendingRequestPlaceholders(configs, teleport, target);
            services.players().formatDisplayName(placeholders, "player", anchor.getUniqueId());
            placeholders.put("expires", expiresIn);
            messenger.send(target, configs.lang().get(receivedByTargetKey), placeholders);
        }

        messenger.send(anchor, configs.lang().get("teleport.tpa.here-sent-all"));
    }

    public static void notifySendResult(Messenger messenger, ConfigManager configs, ServiceManager services, TeleportModule teleport,
                                        Player sender, Player target, TeleportRequestHandler.SendResult result,
                                        String sentToSelfKey, String receivedByTargetKey, Runnable onSent) {
        dispatchSendResult(messenger, configs, services.players(), sender, target, result, () -> {
            onSent.run();

            String expiresIn = result instanceof TeleportRequestHandler.SendResult.Sent sent
                    ? TimeFormatter.expiresIn(sent.expireAfterSeconds(), configs.main().formatting(), configs.lang())
                    : "";

            Map<String, String> senderPlaceholders = new LinkedHashMap<>();
            services.players().formatDisplayName(senderPlaceholders, "target", target.getUniqueId());
            senderPlaceholders.put("expires", expiresIn);
            messenger.send(sender, configs.lang().get(sentToSelfKey), senderPlaceholders);

            Map<String, String> targetPlaceholders = pendingRequestPlaceholders(configs, teleport, target);
            services.players().formatDisplayName(targetPlaceholders, "player", sender.getUniqueId());
            targetPlaceholders.put("expires", expiresIn);
            messenger.send(target, configs.lang().get(receivedByTargetKey), targetPlaceholders);
        });
    }

    private static Map<String, String> pendingRequestPlaceholders(ConfigManager configs, TeleportModule teleport, Player target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("count", String.valueOf(teleport.requests().incomingFor(target.getUniqueId()).size()));
        placeholders.put("limit", PermissionTiers.display(teleport.maxPendingFor(target), configs.lang()));
        return placeholders;
    }

    private static void notifyAutoAccepted(Messenger messenger, ConfigManager configs, ServiceManager services, TeleportModule teleport,
                                           Player sender, Player target, boolean notifySender) {
        Map<String, String> toTarget = new LinkedHashMap<>();
        services.players().formatDisplayName(toTarget, "player", sender.getUniqueId());
        messenger.send(target, configs.lang().get("teleport.tpa.auto-accepted.to-you"), toTarget);

        if (notifySender) {
            Map<String, String> toSender = new LinkedHashMap<>();
            services.players().formatDisplayName(toSender, "player", target.getUniqueId());
            messenger.send(sender, configs.lang().get("teleport.tpa.auto-accepted.by-target"), toSender);
        }

        teleport.acceptRequestWith(target.getUniqueId(), sender.getUniqueId(),
                request -> acceptSession(messenger, configs, services, request.travellerUuid(), request.anchorUuid()));
    }

    public static void dispatchSendResult(Messenger messenger, ConfigManager configs, PlayerService players,
                                          CommandSender sender, Player target, TeleportRequestHandler.SendResult result, Runnable onSent) {
        switch (result) {
            case TeleportRequestHandler.SendResult.Sent ignored -> onSent.run();

            case TeleportRequestHandler.SendResult.Self() ->
                    messenger.send(sender, configs.lang().get("teleport.tpa.errors.self"));

            case TeleportRequestHandler.SendResult.Blocked() -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "player", target.getUniqueId());
                messenger.send(sender, configs.lang().get("extras.block.errors.blocked-by"), placeholders);
            }

            case TeleportRequestHandler.SendResult.TargetToggledOff() -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "target", target.getUniqueId());
                messenger.send(sender, configs.lang().get("teleport.tpa.errors.disabled"), placeholders);
            }

            case TeleportRequestHandler.SendResult.OnCooldown(double remainingSeconds) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "target", target.getUniqueId());
                placeholders.put("timeout", TimeFormatter.duration(remainingSeconds, configs.main().formatting(), configs.lang()));
                messenger.send(sender, configs.lang().get("teleport.tpa.errors.cooldown"), placeholders);
            }

            case TeleportRequestHandler.SendResult.TargetAtLimit(int count, int limit) -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "target", target.getUniqueId());
                placeholders.put("count", String.valueOf(count));
                placeholders.put("limit", String.valueOf(limit));
                messenger.send(sender, configs.lang().get("teleport.tpa.errors.max-pending"), placeholders);
            }
        }
    }

    // Warps

    public static Eligibility.Denied warpNoPermission(NamedLocation warp) {
        return new Eligibility.Denied("teleport.warps.errors.no-permission", Map.of("name", warpSafeName(warp)));
    }

    public static Eligibility.Denied warpBlockedWorld(NamedLocation warp) {
        return new Eligibility.Denied("teleport.warps.errors.blocked-world", Map.of("world", warp.position().world()));
    }

    public static Map<String, String> warpPlaceholders(NamedLocation warp) {
        return Map.of("name", warpSafeName(warp));
    }

    private static String warpSafeName(NamedLocation warp) {
        return MiniText.mini().escapeTags(warp.key());
    }

    // Back

    public static Eligibility.Denied backNoLocation() {
        return new Eligibility.Denied("teleport.back.errors.no-location", Map.of());
    }

    public static Map<String, String> backPlaceholders(Position position) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("world", position.world());
        placeholders.put("x", blockCoordinate(position.x()));
        placeholders.put("y", blockCoordinate(position.y()));
        placeholders.put("z", blockCoordinate(position.z()));
        return placeholders;
    }

    private static String blockCoordinate(double value) {
        return String.valueOf((long) Math.floor(value));
    }
}