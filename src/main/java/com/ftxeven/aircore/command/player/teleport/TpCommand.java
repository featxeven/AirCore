package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.DestinationSource;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TpCommand extends AbstractCommand {

    private static final String KEY = "tp";
    private static final String OTHERS_PERMISSION = Permissions.Command.others(KEY);

    private final Supplier<TeleportModule> teleport;

    public TpCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 1 : 2;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(1, Availability.ofPermission(sender, OTHERS_PERMISSION));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(OTHERS_PERMISSION));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (args.length >= 2) {
            if (!checkPermission(sender, OTHERS_PERMISSION)) {
                return;
            }
            Optional<Player> mover = requireFound(resolver().onlinePlayer(args[0]), sender, args[0]);
            if (mover.isEmpty()) {
                return;
            }
            Optional<Player> destination = requireFound(resolver().onlinePlayer(args[1]), sender, args[1]);
            if (destination.isEmpty()) {
                return;
            }
            beginTeleport(sender, mover.get(), destination.get(), args);
            return;
        }

        Optional<Player> self = requirePlayer(sender);
        if (self.isEmpty()) {
            return;
        }
        Optional<Player> destination = requireFound(resolver().onlinePlayer(args[0]), sender, args[0]);
        if (destination.isEmpty()) {
            return;
        }
        beginTeleport(sender, self.get(), destination.get(), args);
    }

    // Internal

    private void beginTeleport(CommandSender sender, Player mover, Player destinationPlayer, String[] args) {
        boolean moverIsSender = isSelf(sender, mover.getUniqueId());
        boolean destIsSender = isSelf(sender, destinationPlayer.getUniqueId());
        UUID destinationUuid = destinationPlayer.getUniqueId();

        Map<String, String> cancelledPlaceholders = new LinkedHashMap<>();
        services().players().formatDisplayName(cancelledPlaceholders, "target", destinationUuid);

        Scheduler.runEntity(destinationPlayer, () -> {
            DestinationSource destination = DestinationSource.ofPlayer(destinationUuid);
            World destinationWorld = destinationPlayer.getWorld();

            Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, destination, TeleportType.TP,
                    TeleportMessages.session(messenger(), configs(), services(), mover, sender, destinationWorld, destinationUuid,
                            "teleport.tp.cancelled", cancelledPlaceholders,
                            () -> {
                                completeCooldown(sender, args);
                                handleSuccess(sender, mover, destinationPlayer, moverIsSender, destIsSender);
                            })));
        });
    }

    private void handleSuccess(CommandSender sender, Player mover, Player destinationPlayer, boolean moverIsSender, boolean destIsSender) {
        if (moverIsSender) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "target", destinationPlayer.getUniqueId());
            messenger().send(sender, configs().lang().get("teleport.tp.self"), placeholders);
            return;
        }

        if (destIsSender) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "player", mover.getUniqueId());
            messenger().send(sender, configs().lang().get("teleport.tp.other.to-self"), placeholders);
            notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.tp.by.to-self"), Map.of());
        } else {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "player", mover.getUniqueId());
            services().players().formatDisplayName(placeholders, "target", destinationPlayer.getUniqueId());
            messenger().send(sender, configs().lang().get("teleport.tp.other.to-player"), placeholders);

            Map<String, String> targetPlaceholders = new LinkedHashMap<>();
            services().players().formatDisplayName(targetPlaceholders, "target", destinationPlayer.getUniqueId());
            notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.tp.by.to-player"), targetPlaceholders);
        }
    }
}