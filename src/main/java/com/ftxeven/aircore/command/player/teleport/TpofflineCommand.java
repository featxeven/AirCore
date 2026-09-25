package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.StoredLocations;
import com.ftxeven.aircore.module.teleport.DestinationSource;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class TpofflineCommand extends BaseCommand {

    private static final String KEY = "tpoffline";

    private final Supplier<TeleportModule> teleport;

    public TpofflineCommand(Context ctx, Supplier<TeleportModule> teleport) {
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
        Player mover = (Player) sender;
        resolveTarget(sender, args[0], target -> resolveDestination(sender, mover, target, args));
    }

    private void resolveDestination(CommandSender sender, Player mover, PlayerTargetResolver.Target target, String[] args) {
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            teleportToOnline(sender, mover, online, args);
            return;
        }

        Position lastLocation = target.profile().lastLocation();
        if (lastLocation == null) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "target", target.profile());
            messenger().send(sender, configs().lang().get("teleport.tpoffline.errors.no-location"), placeholders);
            return;
        }

        Location destination = switch (StoredLocations.resolve(lastLocation)) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) {
            return;
        }

        Scheduler.runEntity(mover, () -> teleportToLastLocation(sender, mover, target, destination, args));
    }

    // Online target

    private void teleportToOnline(CommandSender sender, Player mover, Player online, String[] args) {
        UUID onlineUuid = online.getUniqueId();

        Map<String, String> cancelledPlaceholders = new LinkedHashMap<>();
        services().players().formatDisplayName(cancelledPlaceholders, "target", onlineUuid);

        Scheduler.runEntity(online, () -> {
            DestinationSource destination = DestinationSource.ofPlayer(onlineUuid);
            World onlineWorld = online.getWorld();

            Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, destination, TeleportType.TPOFFLINE,
                    TeleportMessages.session(messenger(), configs(), services(), mover, sender, onlineWorld, onlineUuid,
                            "teleport.tp.cancelled", cancelledPlaceholders,
                            () -> {
                                completeCooldown(sender, args);
                                handleOnlineSuccess(sender, online);
                            })));
        });
    }

    private void handleOnlineSuccess(CommandSender sender, Player online) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", online.getUniqueId());
        messenger().send(sender, configs().lang().get("teleport.tp.self"), placeholders);
    }

    // Offline target

    private void teleportToLastLocation(CommandSender sender, Player mover, PlayerTargetResolver.Target target, Location destination, String[] args) {
        Map<String, String> cancelledPlaceholders = new LinkedHashMap<>();
        services().players().formatDisplayName(cancelledPlaceholders, "target", target.profile());

        teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination), TeleportType.TPOFFLINE,
                TeleportMessages.session(messenger(), configs(), services(), mover, sender, destination.getWorld(), null,
                        "teleport.tpoffline.cancelled", cancelledPlaceholders,
                        () -> {
                            completeCooldown(sender, args);
                            handleOfflineSuccess(sender, target);
                        }));
    }

    private void handleOfflineSuccess(CommandSender sender, PlayerTargetResolver.Target target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", target.profile());
        messenger().send(sender, configs().lang().get("teleport.tpoffline.success"), placeholders);
    }
}