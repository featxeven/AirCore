package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.model.TeleportType;
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
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Supplier;

public final class TpposCommand extends BaseCommand {

    private static final String KEY = "tppos";
    private static final String OTHERS_PERMISSION = Permissions.Command.others(KEY);
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;

    public TpposCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) { return 3; }

    @Override
    public int maxArgs(CommandSender sender) {
        return hasExtraArg(sender) ? 4 : 3;
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), hasExtraArg(sender));
    }

    private boolean hasExtraArg(CommandSender sender) {
        return sender.hasPermission(OTHERS_PERMISSION) || sender.hasPermission(ALL_PERMISSION);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String playerArg = args.length >= 4 ? args[3] : null;

        if (playerArg == null) {
            Optional<Player> playerOpt = requirePlayer(sender);
            if (playerOpt.isEmpty()) {
                return;
            }
            teleportSelf(sender, playerOpt.get(), args);
            return;
        }

        if (selectors().isAll(playerArg)) {
            teleportAll(sender, args);
            return;
        }

        if (!checkPermission(sender, OTHERS_PERMISSION)) {
            return;
        }

        if (sender instanceof Player self && resolver().matchesSelf(self, playerArg)) {
            teleportSelf(sender, self, args);
            return;
        }

        Optional<Player> mover = requireFound(resolver().onlinePlayer(playerArg), sender, playerArg);
        if (mover.isEmpty()) {
            return;
        }
        teleportOther(sender, mover.get(), args);
    }

    // Self path

    private void teleportSelf(CommandSender sender, Player player, String[] args) {
        Optional<Location> parsed = parseDestination(sender, player.getLocation(), args);
        if (parsed.isEmpty()) {
            return;
        }
        Location destination = parsed.get();

        teleport.get().teleportWithCountdown(player, sender, DestinationSource.fixed(destination), TeleportType.TPPOS,
                TeleportMessages.session(messenger(), configs(), services(), player, sender, destination.getWorld(), null,
                        "teleport.tppos.cancelled", coords(destination),
                        () -> {
                            completeCooldown(sender, args);
                            messenger().send(player, configs().lang().get("teleport.tppos.self"), coords(destination));
                        }));
    }

    // Single-target path

    private void teleportOther(CommandSender sender, Player mover, String[] args) {
        Scheduler.runEntity(mover, () -> {
            Optional<Location> parsed = parseDestination(sender, mover.getLocation(), args);
            if (parsed.isEmpty()) {
                return;
            }
            Location destination = parsed.get();

            teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination), TeleportType.TPPOS,
                    TeleportMessages.session(messenger(), configs(), services(), mover, sender, destination.getWorld(), null,
                            "teleport.tppos.cancelled", coords(destination),
                            () -> {
                                completeCooldown(sender, args);
                                handleOtherSuccess(sender, mover, destination);
                            }));
        });
    }

    private void handleOtherSuccess(CommandSender sender, Player mover, Location destination) {
        messenger().send(sender, configs().lang().get("teleport.tppos.other"), coordsWithPlayer(mover, destination));
        notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.tppos.by"), coords(destination));
    }

    // Everyone-online path

    private void teleportAll(CommandSender sender, String[] args) {
        if (!checkPermission(sender, ALL_PERMISSION)) {
            return;
        }
        Optional<Player> referenceOpt = requirePlayer(sender);
        if (referenceOpt.isEmpty()) {
            return;
        }
        Player senderPlayer = referenceOpt.get();

        Optional<Location> parsed = parseDestination(sender, senderPlayer.getLocation(), args);
        if (parsed.isEmpty()) {
            return;
        }
        Location destination = parsed.get();

        completeCooldown(sender, args);

        UUID senderUuid = senderPlayer.getUniqueId();
        Map<String, String> cancelledPlaceholders = coords(destination);

        for (Player target : List.copyOf(Bukkit.getOnlinePlayers())) {
            Scheduler.runEntity(target, () ->
                    teleport.get().teleportWithCountdown(target, sender, DestinationSource.fixed(destination.clone()), TeleportType.TPPOS,
                            TeleportMessages.session(messenger(), configs(), services(), target, target, destination.getWorld(), null,
                                    "teleport.tppos.cancelled", cancelledPlaceholders,
                                    () -> handleAllSuccess(sender, target, senderUuid, destination))));
        }
        messenger().send(sender, configs().lang().get("teleport.tppos.all"), coords(destination));
    }

    private void handleAllSuccess(CommandSender sender, Player target, UUID senderUuid, Location destination) {
        if (!target.getUniqueId().equals(senderUuid)) {
            notifyTarget(sender, target.getUniqueId(), configs().lang().get("teleport.tppos.by"), coords(destination));
        }
    }

    // Shared

    private Optional<Location> parseDestination(CommandSender sender, Location current, String[] args) {
        OptionalDouble x = parseCoordinate(args[0], current.getX());
        OptionalDouble y = parseCoordinate(args[1], current.getY());
        OptionalDouble z = parseCoordinate(args[2], current.getZ());
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            messenger().send(sender, configs().lang().get("teleport.tppos.errors.invalid-coords"));
            return Optional.empty();
        }
        return Optional.of(new Location(current.getWorld(), x.getAsDouble(), y.getAsDouble(), z.getAsDouble(),
                current.getYaw(), current.getPitch()));
    }

    private Map<String, String> coords(Location location) {
        return Map.of(
                "x", format(location.getX()),
                "y", format(location.getY()),
                "z", format(location.getZ())
        );
    }

    private Map<String, String> coordsWithPlayer(Player mover, Location location) {
        Map<String, String> map = new LinkedHashMap<>(coords(location));
        services().players().formatDisplayName(map, "player", mover.getUniqueId());
        return map;
    }

    private String format(double value) {
        return "%.2f".formatted(value);
    }

    private OptionalDouble parseCoordinate(String token, double current) {
        if (token.equals("~")) {
            return OptionalDouble.of(current);
        }
        if (token.startsWith("~")) {
            OptionalDouble offset = parseDouble(token.substring(1));
            return offset.isPresent() ? OptionalDouble.of(current + offset.getAsDouble()) : OptionalDouble.empty();
        }
        return parseDouble(token);
    }

    private OptionalDouble parseDouble(String raw) {
        try {
            return OptionalDouble.of(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}