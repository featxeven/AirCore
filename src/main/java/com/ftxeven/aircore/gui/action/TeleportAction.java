package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.StoredLocations;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.homes.HomeTeleporter;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.module.teleport.DestinationSource;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class TeleportAction implements ConfirmableAction {

    private static final String VERB = "teleport";
    private static final String BACK_KEY = "back";

    private static final String TARGET_PERMISSION = Permissions.Command.of("playerhome");

    private final ConfigManager configs;
    private final Supplier<TeleportModule> teleport;
    private final Supplier<HomesModule> homes;
    private final ServiceManager services;
    private final Logger logger;

    public TeleportAction(ConfigManager configs, Supplier<TeleportModule> teleport, Supplier<HomesModule> homes,
                          ServiceManager services, Logger logger) {
        this.configs = configs;
        this.teleport = teleport;
        this.homes = homes;
        this.services = services;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "warp" -> teleportWarp(context, args);
            case "home" -> teleportHome(context, args);
            case "back" -> teleportBack(context);
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
            case "warp" -> decideWarp(context, args);
            case "home" -> decideHome(context, args);
            case "back" -> decideBack(context);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield Decision.immediate();
            }
        };
    }

    // warp

    private void teleportWarp(ActionContext context, String args) {
        String warpName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (warpName == null) {
            logger.warning("Action '[teleport] type:warp' has no 'id:' argument and this screen has no warp "
                    + "context to fall back on - it will not do anything until this is fixed");
            return;
        }

        Optional<NamedLocation> resolved = teleport.get().findWarp(warpName);
        if (resolved.isEmpty()) {
            logger.warning("Action '[teleport] type:warp' points at warp '" + warpName + "', which isn't a "
                    + "registered warp - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        NamedLocation warp = resolved.get();

        if (!Permissions.Access.hasWarp(player, warp.key())) {
            TeleportMessages.warpNoPermission(warp).send(player, configs, context.messenger());
            return;
        }
        if (teleport.get().isWarpingDisabled(player, warp.position().world())) {
            TeleportMessages.warpBlockedWorld(warp).send(player, configs, context.messenger());
            return;
        }

        Location destination = switch (StoredLocations.resolve(warp.position())) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                context.messenger().send(player, configs.lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) {
            return;
        }

        String safeName = MiniText.mini().escapeTags(warp.key());
        Map<String, String> cancelledPlaceholders = Map.of("name", safeName);

        Scheduler.runEntity(player, () -> teleport.get().teleportWithCountdown(player, player, DestinationSource.fixed(destination), TeleportType.WARP,
                TeleportMessages.session(context.messenger(), configs, services, player, player, destination.getWorld(), null,
                        "teleport.warps.cancelled", cancelledPlaceholders,
                        () -> context.messenger().send(player, configs.lang().get("teleport.warps.teleported.self"), Map.of("name", safeName)))));
    }

    private Decision decideWarp(ActionContext context, Map<String, String> args) {
        String warpName = args.get("id");
        if (warpName == null || warpName.isBlank()) {
            return Decision.immediate();
        }

        Optional<NamedLocation> resolved = teleport.get().findWarp(warpName);
        if (resolved.isEmpty()) {
            return Decision.immediate();
        }

        NamedLocation warp = resolved.get();
        Player player = context.viewer();

        if (!Permissions.Access.hasWarp(player, warp.key())) {
            return Decision.denied(TeleportMessages.warpNoPermission(warp));
        }
        if (teleport.get().isWarpingDisabled(player, warp.position().world())) {
            return Decision.denied(TeleportMessages.warpBlockedWorld(warp));
        }

        return Decision.needsConfirmation(
                TeleportMessages.warpPlaceholders(warp),
                GuiFlags.forWarp(player, teleport.get(), warp));
    }

    // home

    private void teleportHome(ActionContext context, String args) {
        String homeName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (homeName == null) {
            logger.warning("Action '[teleport] type:home' has no 'id:' argument and this screen has no home "
                    + "context to fall back on - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);
        boolean self = owner.equals(player.getUniqueId());

        Optional<Home> resolved = findClickedHome(player, owner, homeName);
        if (resolved.isEmpty()) {
            staleEntry(homeName).send(player, configs, context.messenger());
            return;
        }
        Home home = resolved.get();

        Map<String, String> placeholders = new LinkedHashMap<>();
        if (!self) {
            services.players().formatDisplayName(placeholders, "target", owner);
        }
        placeholders.put("name", home.name());
        String successKey = self ? "homes.teleported.self" : "homes.teleported.other";
        String cancelledKey = self ? "homes.cancelled.self" : "homes.cancelled.other";

        Scheduler.runEntity(player, () -> HomeTeleporter.to(context.messenger(), configs, services, homes, teleport,
                player, home.position(), successKey, cancelledKey, placeholders, () -> { }));
    }

    private Decision decideHome(ActionContext context, Map<String, String> args) {
        // like warp, the id has to be explicit here: these tokens are forwarded to the confirm screen
        String homeName = args.get("id");
        if (homeName == null || homeName.isBlank()) {
            return Decision.immediate();
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);

        Optional<Home> resolved = findClickedHome(player, owner, homeName);
        if (resolved.isEmpty()) {
            return Decision.denied(staleEntry(homeName));
        }
        Home home = resolved.get();

        if (homes.get().blocksWorld(player, home.position().world())) {
            return Decision.denied(homeBlockedWorld(home));
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        if (!owner.equals(player.getUniqueId())) {
            services.players().formatDisplayName(placeholders, "target", owner);
        }
        placeholders.putAll(GuiPlaceholders.homeWithIcon(configs, home));

        return Decision.needsConfirmation(placeholders, GuiFlags.forHome(player, configs.filter(), owner, home, homes.get().homeResolver(owner)));
    }

    private Optional<Home> findClickedHome(Player viewer, UUID owner, String homeName) {
        boolean self = owner.equals(viewer.getUniqueId());
        if (!self && !viewer.hasPermission(TARGET_PERMISSION)) {
            return Optional.empty();
        }
        return homes.get().find(owner, homeName);
    }

    private static Eligibility.Denied staleEntry(String homeName) {
        return new Eligibility.Denied("errors.general.stale-entry", Map.of("name", homeName));
    }

    private static Eligibility.Denied homeBlockedWorld(Home home) {
        return new Eligibility.Denied("homes.errors.blocked-world", Map.of("world", home.position().world()));
    }

    // back

    private void teleportBack(ActionContext context) {
        Player player = context.viewer();
        String commandName = configs.commands().findCommandOrDisabled(BACK_KEY).name();

        Scheduler.runEntity(player, () -> teleport.get().teleportBack(player,
                () -> services.commandCooldowns().complete(commandName, player, new String[0])));
    }

    private Decision decideBack(ActionContext context) {
        Player player = context.viewer();

        Optional<Position> last = teleport.get().back().peek(player.getUniqueId());
        if (last.isEmpty()) {
            return Decision.denied(TeleportMessages.backNoLocation());
        }

        return Decision.needsConfirmation(
                TeleportMessages.backPlaceholders(last.get()),
                GuiFlags.forBack(player, teleport.get()));
    }
}