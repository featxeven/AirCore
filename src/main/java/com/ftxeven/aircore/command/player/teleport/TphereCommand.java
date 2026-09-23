package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TphereCommand extends AbstractCommand {

    private static final String KEY = "tphere";
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;

    public TphereCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
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

        if (selectors().isAll(args[0])) {
            if (!checkPermission(sender, ALL_PERMISSION)) {
                return;
            }
            Optional<List<Player>> movers = requireOtherPlayersOnline(sender, anchor.getUniqueId());
            if (movers.isEmpty()) {
                return;
            }
            teleportAll(sender, anchor, movers.get(), args);
            return;
        }

        Optional<Player> mover = requireFound(resolver().onlinePlayer(args[0]), sender, args[0]);
        if (mover.isEmpty()) {
            return;
        }
        teleportOne(sender, anchor, mover.get(), args);
    }

    // Single-target path

    private void teleportOne(CommandSender sender, Player anchor, Player mover, String[] args) {
        completeCooldown(sender, args);
        UUID anchorUuid = anchor.getUniqueId();

        Map<String, String> cancelledPlaceholders = new LinkedHashMap<>();
        services().players().formatDisplayName(cancelledPlaceholders, "target", anchorUuid);

        Scheduler.runEntity(anchor, () -> {
            DestinationSource destination = DestinationSource.ofPlayer(anchorUuid);
            World anchorWorld = anchor.getWorld();

            Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, destination, TeleportType.TPHERE,
                    TeleportMessages.session(messenger(), configs(), services(), mover, sender, anchorWorld, anchorUuid,
                            "teleport.tp.cancelled", cancelledPlaceholders,
                            () -> handleSingleSuccess(sender, anchor, mover))));
        });
    }

    private void handleSingleSuccess(CommandSender sender, Player anchor, Player mover) {
        if (mover.getUniqueId().equals(anchor.getUniqueId())) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "target", anchor.getUniqueId());
            messenger().send(sender, configs().lang().get("teleport.tp.self"), placeholders);
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", mover.getUniqueId());
        messenger().send(sender, configs().lang().get("teleport.tp.other.to-self"), placeholders);
        notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.tp.by.to-self"), Map.of());
    }

    // Everyone-online path

    private void teleportAll(CommandSender sender, Player anchor, List<Player> movers, String[] args) {
        completeCooldown(sender, args);
        UUID anchorUuid = anchor.getUniqueId();

        Map<String, String> cancelledPlaceholders = new LinkedHashMap<>();
        services().players().formatDisplayName(cancelledPlaceholders, "target", anchorUuid);

        for (Player mover : movers) {
            Scheduler.runEntity(anchor, () -> {
                DestinationSource destination = DestinationSource.ofPlayer(anchorUuid);
                World anchorWorld = anchor.getWorld();

                Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, destination, TeleportType.TPHERE,
                        TeleportMessages.session(messenger(), configs(), services(), mover, mover, anchorWorld, anchorUuid,
                                "teleport.tp.cancelled", cancelledPlaceholders,
                                () -> handleAllSuccess(sender, mover))));
            });
        }
        messenger().send(sender, configs().lang().get("teleport.tp.all"));
    }

    private void handleAllSuccess(CommandSender sender, Player mover) {
        notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.tp.by.to-self"), Map.of());
    }
}