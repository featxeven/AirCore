package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.NamedLocation;
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
import java.util.function.Function;
import java.util.function.Supplier;

public final class WarpCommand extends BaseCommand {

    private static final String KEY = "warp";
    private static final String OTHERS_PERMISSION = Permissions.Command.others(KEY);
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;
    private final PluginGuiManager guis;
    private final ConfirmationFlow confirmations;

    public WarpCommand(Context ctx, Supplier<TeleportModule> teleport, PluginGuiManager guis) {
        super(ctx, KEY);
        this.teleport = teleport;
        this.guis = guis;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(1, Availability.ofConfig(canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), canTargetOthers(sender));
    }

    private boolean canTargetOthers(CommandSender sender) {
        return sender.hasPermission(OTHERS_PERMISSION) || sender.hasPermission(ALL_PERMISSION);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (args.length == 0) {
            openMainOrList(sender, args);
            return;
        }

        String name = args[0];
        Optional<NamedLocation> resolved = teleport.get().findWarp(name);
        if (resolved.isEmpty()) {
            messenger().send(sender, configs().lang().get("teleport.warps.errors.not-found"), Map.of("name", escapeUserInput(name)));
            return;
        }
        NamedLocation warp = resolved.get();

        if (teleport.get().isWarpingDisabled(sender, warp.position().world())) {
            TeleportMessages.warpBlockedWorld(warp).send(sender, configs(), messenger());
            return;
        }

        if (args.length == 1) {
            Optional<Player> self = requirePlayer(sender);
            if (self.isEmpty()) return;
            if (!checkWarpAccess(self.get(), warp)) return;
            warpSelf(self.get(), warp, args);
            return;
        }

        String targetToken = args[1];

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, ALL_PERMISSION)) return;
            teleportAll(sender, warp, args);
            return;
        }

        if (!checkPermission(sender, OTHERS_PERMISSION)) return;

        if (sender instanceof Player player && resolver().matchesSelf(player, targetToken)) {
            if (!checkWarpAccess(player, warp)) return;
            warpSelf(player, warp, args);
            return;
        }

        Optional<Player> mover = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (mover.isEmpty()) return;
        teleportOne(sender, mover.get(), warp, args);
    }

    private boolean checkWarpAccess(Player player, NamedLocation warp) {
        if (Permissions.Access.hasWarp(player, warp.key())) {
            return true;
        }
        TeleportMessages.warpNoPermission(warp).send(player, configs(), messenger());
        return false;
    }

    // Listing / main menu

    private void openMainOrList(CommandSender sender, String[] args) {
        Optional<String> menuGui = config().gui("menu", args);
        if (sender instanceof Player player && menuGui.isPresent() && GuiActions.guiEnabled(guis.guis(), menuGui.get())) {
            Function<String, String> flags = GuiFlags.forWarp(player, teleport.get(), null);
            guis.guis().open(player, menuGui.get(), new LinkedHashMap<>(), new OpenOptions(flags, Map.of(), OpenOptions.Kind.ENTRY, List.of()));
            return;
        }
        listWarps(sender);
    }

    private void listWarps(CommandSender sender) {
        List<NamedLocation> warps = teleport.get().findAllWarps().stream()
                .filter(warp -> Permissions.Access.hasWarp(sender, warp.key()))
                .toList();
        if (warps.isEmpty()) {
            messenger().send(sender, configs().lang().get("teleport.warps.list.empty"));
            return;
        }

        String entryTemplate = configs().lang().get("teleport.warps.list.entry").getFirst();
        String separator = configs().lang().get("teleport.warps.list.separator").getFirst();

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < warps.size(); i++) {
            if (i > 0) {
                joined.append(separator);
            }
            joined.append(entryTemplate.replace("%name%", escapeUserInput(warps.get(i).key())));
        }

        messenger().send(sender, configs().lang().get("teleport.warps.list.header"), Map.of(
                "count", String.valueOf(warps.size()),
                "warps", joined.toString()
        ));
    }

    // Self warping

    private void warpSelf(Player player, NamedLocation warp, String[] args) {
        confirmations.guiOnly(player, config().gui("confirm", args),
                Map.of("id", warp.key()), TeleportMessages.warpPlaceholders(warp),
                GuiFlags.forWarp(player, teleport.get(), warp),
                () -> teleportOne(player, player, warp, args));
    }

    // Single-target path (self or others)

    private void teleportOne(CommandSender sender, Player mover, NamedLocation warp, String[] args) {
        Location destination = switch (StoredLocations.resolve(warp.position())) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) return;

        boolean moverIsSender = isSelf(sender, mover.getUniqueId());
        String safeName = escapeUserInput(warp.key());
        Map<String, String> cancelledPlaceholders = Map.of("name", safeName);

        Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination), TeleportType.WARP,
                TeleportMessages.session(messenger(), configs(), services(), mover, sender, destination.getWorld(), null,
                        "teleport.warps.cancelled", cancelledPlaceholders,
                        () -> {
                            completeCooldown(sender, args);
                            handleSuccess(sender, mover, moverIsSender, safeName);
                        })));
    }

    private void handleSuccess(CommandSender sender, Player mover, boolean moverIsSender, String safeName) {
        if (moverIsSender) {
            messenger().send(sender, configs().lang().get("teleport.warps.teleported.self"), Map.of("name", safeName));
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", mover.getUniqueId());
        placeholders.put("name", safeName);
        messenger().send(sender, configs().lang().get("teleport.warps.teleported.other"), placeholders);
        notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.warps.teleported.by"), Map.of("name", safeName));
    }

    // Everyone-online path

    private void teleportAll(CommandSender sender, NamedLocation warp, String[] args) {
        Location destination = switch (StoredLocations.resolve(warp.position())) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) return;

        completeCooldown(sender, args);
        String safeName = escapeUserInput(warp.key());
        Map<String, String> cancelledPlaceholders = Map.of("name", safeName);

        for (Player mover : List.copyOf(Bukkit.getOnlinePlayers())) {
            Scheduler.runEntity(mover, () -> teleport.get().teleportWithCountdown(mover, sender, DestinationSource.fixed(destination.clone()), TeleportType.WARP,
                    TeleportMessages.session(messenger(), configs(), services(), mover, mover, destination.getWorld(), null,
                            "teleport.warps.cancelled", cancelledPlaceholders,
                            () -> handleAllSuccess(sender, mover, safeName))));
        }
        messenger().send(sender, configs().lang().get("teleport.warps.teleported.all"), Map.of("name", safeName));
    }

    private void handleAllSuccess(CommandSender sender, Player mover, String safeName) {
        if (!isSelf(sender, mover.getUniqueId())) {
            notifyTarget(sender, mover.getUniqueId(), configs().lang().get("teleport.warps.teleported.by"), Map.of("name", safeName));
        }
    }
}