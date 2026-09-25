package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SpawnCommand extends BaseCommand {

    private static final String KEY = "spawn";
    private static final String OTHERS_PERMISSION = Permissions.Command.others(KEY);
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;

    public SpawnCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) { return sender instanceof Player ? 0 : 1; }

    @Override
    public int maxArgs(CommandSender sender) { return hasExtraArg(sender) ? 1 : 0; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), hasExtraArg(sender));
    }

    private boolean hasExtraArg(CommandSender sender) {
        return sender.hasPermission(OTHERS_PERMISSION) || sender.hasPermission(ALL_PERMISSION);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String typed = args.length > 0 ? args[0] : null;

        if (sender instanceof Player player && typed == null) {
            teleportOne(sender, player, args);
            return;
        }

        if (selectors().isAll(typed)) {
            if (!checkPermission(sender, ALL_PERMISSION)) {
                return;
            }
            teleportAll(sender, args);
            return;
        }

        if (!checkPermission(sender, OTHERS_PERMISSION)) {
            return;
        }

        if (sender instanceof Player player && resolver().matchesSelf(player, typed)) {
            teleportOne(sender, player, args);
            return;
        }

        Optional<Player> mover = requireFound(resolver().onlinePlayer(typed), sender, typed);
        if (mover.isEmpty()) {
            return;
        }
        teleportOne(sender, mover.get(), args);
    }

    // Single-target path (self or others)

    private void teleportOne(CommandSender sender, Player mover, String[] args) {
        Optional<TeleportModule.SpawnResolution> spawn = teleport.get().spawnPositionFor(mover);
        if (spawn.isEmpty()) {
            messenger().send(sender, configs().lang().get("teleport.spawn.errors.not-set"));
            return;
        }
        TeleportModule.SpawnResolution resolution = spawn.get();

        Location destination = switch (StoredLocations.resolve(resolution.position())) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) {
            return;
        }

        boolean moverIsSender = isSelf(sender, mover.getUniqueId());

        Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination), TeleportType.SPAWN,
                TeleportMessages.session(messenger(), configs(), services(), mover, sender, destination.getWorld(), null,
                        "teleport.spawn.cancelled", Map.of(),
                        () -> {
                            completeCooldown(sender, args);
                            handleSuccess(sender, mover, moverIsSender, resolution);
                        })));
    }

    private void handleSuccess(CommandSender sender, Player mover, boolean moverIsSender, TeleportModule.SpawnResolution resolution) {
        if (moverIsSender) {
            switch (resolution) {
                case TeleportModule.SpawnResolution.Group(String group, Position ignored) ->
                        messenger().send(sender, configs().lang().get("teleport.spawn.teleported.group"), Map.of("group", group));
                case TeleportModule.SpawnResolution.Default ignored ->
                        messenger().send(sender, configs().lang().get("teleport.spawn.teleported.self"));
            }
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", mover.getUniqueId());
        messenger().send(sender, configs().lang().get("teleport.spawn.teleported.other"), placeholders);
        notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.spawn.teleported.by"), Map.of());
    }

    // Everyone-online path

    private void teleportAll(CommandSender sender, String[] args) {
        completeCooldown(sender, args);

        for (Player mover : List.copyOf(Bukkit.getOnlinePlayers())) {
            Optional<TeleportModule.SpawnResolution> spawn = teleport.get().spawnPositionFor(mover);
            if (spawn.isEmpty()) {
                continue;
            }
            if (!(StoredLocations.resolve(spawn.get().position()) instanceof StoredLocations.Resolution.Ready(Location destination))) {
                continue;
            }

            Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination), TeleportType.SPAWN,
                    TeleportMessages.session(messenger(), configs(), services(), mover, mover, destination.getWorld(), null,
                            "teleport.spawn.cancelled", Map.of(),
                            () -> handleAllSuccess(sender, mover))));
        }
        messenger().send(sender, configs().lang().get("teleport.spawn.teleported.all"));
    }

    private void handleAllSuccess(CommandSender sender, Player mover) {
        if (!isSelf(sender, mover.getUniqueId())) {
            notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.spawn.teleported.by"), Map.of());
        }
    }
}