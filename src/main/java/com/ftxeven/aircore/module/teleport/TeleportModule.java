package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.LocationCache;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.GroupResolver;
import com.ftxeven.aircore.module.StoredLocations;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.module.extras.ExtrasModule;
import com.ftxeven.aircore.module.teleport.back.BackLocationTracker;
import com.ftxeven.aircore.module.teleport.countdown.CountdownGate;
import com.ftxeven.aircore.module.teleport.request.TeleportRequestHandler;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public final class TeleportModule {

    public interface TeleportSession {
        default void onTick(int secondsRemaining) { }
        default void onCancel(CountdownGate.CancelReason reason) { }
        void onComplete(TeleportExecutor.Outcome outcome);
    }

    public sealed interface AcceptOutcome {
        record Started(TeleportRequestHandler.PendingRequest request) implements AcceptOutcome {}
        record MoverOffline(TeleportRequestHandler.PendingRequest request) implements AcceptOutcome {}
        record NotFound() implements AcceptOutcome {}
    }

    private final ConfigManager configs;
    private final LocationCache locations;
    private final ServiceManager services;
    private final ExtrasModule extras;
    private final GroupResolver groups;
    private final Messenger messenger;

    private final BackLocationTracker back;
    private final TeleportExecutor executor;
    private final CountdownGate countdown;
    private final TeleportRequestHandler requests;

    public TeleportModule(ConfigManager configs, LocationCache locations, ServiceManager services,
                          ExtrasModule extras, GroupResolver groups, Messenger messenger) {
        this.configs = configs;
        this.locations = locations;
        this.services = services;
        this.extras = extras;
        this.groups = groups;
        this.messenger = messenger;

        this.back = new BackLocationTracker(configs::teleport);
        this.executor = new TeleportExecutor(configs::teleport, back);
        this.countdown = new CountdownGate(configs::teleport);
        this.requests = new TeleportRequestHandler(configs::teleport);
        this.requests.onExpire(this::handleRequestExpired);
    }

    public CountdownGate countdown() { return countdown; }
    public BackLocationTracker back() { return back; }
    public TeleportRequestHandler requests() { return requests; }

    public void teleportWithCountdown(Player player, CommandSender initiator, DestinationSource destination, TeleportType type, TeleportSession session) {
        destination.resolve(initial -> {
            if (initial.isEmpty()) {
                session.onComplete(new TeleportExecutor.Outcome.DestinationOffline());
                return;
            }
            if (executor.blocksDisabledWorld(player, initiator, initial.get().getWorld(), type)) {
                session.onComplete(new TeleportExecutor.Outcome.DisabledWorld());
                return;
            }

            countdown.start(player, initiator, type, new CountdownGate.Session() {
                @Override
                public void onTick(int secondsRemaining) {
                    session.onTick(secondsRemaining);
                }

                @Override
                public void onCancel(CountdownGate.CancelReason reason) {
                    session.onCancel(reason);
                }

                @Override
                public void onComplete() {
                    destination.resolve(current -> {
                        if (current.isEmpty()) {
                            session.onComplete(new TeleportExecutor.Outcome.DestinationOffline());
                            return;
                        }
                        if (executor.blocksDisabledWorld(player, initiator, current.get().getWorld(), type)) {
                            session.onComplete(new TeleportExecutor.Outcome.DisabledWorld());
                            return;
                        }
                        executor.resolveDestination(current.get(), type, resolved -> {
                            if (resolved.isEmpty()) {
                                session.onComplete(new TeleportExecutor.Outcome.UnsafeDestination());
                                return;
                            }
                            executor.finish(player, resolved.get(), type);
                            session.onComplete(new TeleportExecutor.Outcome.Teleported());
                        });
                    });
                }
            });
        });
    }

    public void teleportBack(Player player, Runnable onSuccess) {
        Optional<Position> last = back.peek(player.getUniqueId());
        if (last.isEmpty()) {
            messenger.send(player, configs.lang().get("teleport.back.errors.no-location"));
            return;
        }
        Position position = last.get();

        Location destination = switch (StoredLocations.resolve(position)) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger.send(player, configs.lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) {
            return;
        }

        teleportWithCountdown(player, player, DestinationSource.fixed(destination), TeleportType.BACK,
                TeleportMessages.session(messenger, configs, services, player, player, destination.getWorld(), null,
                        "teleport.back.cancelled", Map.of(),
                        () -> {
                            onSuccess.run();
                            back.remove(player.getUniqueId(), position);
                            messenger.send(player, configs.lang().get("teleport.back.success"));
                        }));
    }

    // whether the player has a return point that can currently be teleported to
    public boolean canBack(Player player) {
        return back.peek(player.getUniqueId())
                .map(position -> StoredLocations.resolve(position) instanceof StoredLocations.Resolution.Ready)
                .orElse(false);
    }

    // Teleport requests (TPA / TPAHERE)

    public boolean sendConfirmationRequired(Player sender) {
        if (!configs.teleport().requests().requireConfirmation()) {
            return false;
        }
        return services.players().peek(sender.getUniqueId())
                .map(profile -> profile.toggles().tpConfirm())
                .orElse(true);
    }

    public Optional<TeleportRequestHandler.SendResult> previewRequest(Player sender, Player target) {
        Optional<TeleportRequestHandler.SendResult> blocked = blockedFromRequest(sender, target);
        if (blocked.isPresent()) {
            return blocked;
        }
        return requests.preview(sender, target.getUniqueId(), targetAcceptsRequests(target), maxPendingFor(target));
    }

    public TeleportRequestHandler.SendResult sendRequest(Player sender, Player target, TeleportType type) {
        Optional<TeleportRequestHandler.SendResult> blocked = blockedFromRequest(sender, target);
        return blocked.orElseGet(() -> requests.send(sender, target.getUniqueId(), type, targetAcceptsRequests(target), maxPendingFor(target)));
    }

    public int maxPendingFor(Permissible permissible) {
        double resolved = PermissionTiers.resolveTier(permissible, Permissions.Bypass.MAX_PENDING, configs.teleport().requests().maxPending());
        return (int) resolved;
    }

    public OptionalDouble previewRequestAll(Player sender) {
        return requests.previewAll(sender);
    }

    public void markRequestAllSent(Player sender) {
        requests.markAllSent(sender);
    }

    private Optional<TeleportRequestHandler.SendResult> blockedFromRequest(Player sender, Player target) {
        boolean blocked = extras.blocks().blocksInteraction(ExtrasConfig.BlockAction.TELEPORT_REQUESTS, target.getUniqueId(), sender);
        return blocked ? Optional.of(new TeleportRequestHandler.SendResult.Blocked()) : Optional.empty();
    }

    private boolean targetAcceptsRequests(Player target) {
        return services.players().peek(target.getUniqueId()).map(profile -> profile.toggles().tp()).orElse(true);
    }

    public boolean targetAutoAccepts(Player target) {
        return services.players().peek(target.getUniqueId()).map(profile -> profile.toggles().tpAutoAccept()).orElse(false);
    }

    public void notifyIfAfk(Player sender, Player target) {
        extras.afk().notifyIfAfk(sender, target.getUniqueId(), ExtrasConfig.AfkNotifyAction.TELEPORT_REQUESTS);
    }

    public AcceptOutcome acceptRequest(UUID targetUuid, UUID senderUuid, TeleportSession session) {
        return acceptRequestWith(targetUuid, senderUuid, request -> session);
    }

    public AcceptOutcome acceptRequestWith(UUID targetUuid, UUID senderUuid,
                                           Function<TeleportRequestHandler.PendingRequest, TeleportSession> sessionFor) {
        Optional<TeleportRequestHandler.PendingRequest> resolved = requests.accept(targetUuid, senderUuid);
        if (resolved.isEmpty()) {
            return new AcceptOutcome.NotFound();
        }
        TeleportRequestHandler.PendingRequest request = resolved.get();

        Player traveller = Bukkit.getPlayer(request.travellerUuid());
        Player anchor = Bukkit.getPlayer(request.anchorUuid());
        if (traveller == null || anchor == null) {
            return new AcceptOutcome.MoverOffline(request);
        }

        TeleportSession session = sessionFor.apply(request);
        UUID anchorUuid = request.anchorUuid();
        Scheduler.runEntity(anchor, () -> {
            DestinationSource destination = DestinationSource.ofPlayer(anchorUuid);
            teleportWithCountdown(traveller, traveller, destination, request.type(), session);
        });

        return new AcceptOutcome.Started(request);
    }

    private void handleRequestExpired(TeleportRequestHandler.PendingRequest request) {
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services.players().formatDisplayName(placeholders, "target", request.target());
            messenger.send(sender, configs.lang().get("teleport.tpa.expired.outgoing"), placeholders);
        }

        Player target = Bukkit.getPlayer(request.target());
        if (target != null) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services.players().formatDisplayName(placeholders, "player", request.sender());
            messenger.send(target, configs.lang().get("teleport.tpa.expired.incoming"), placeholders);
        }
    }

    // Warps

    public Optional<NamedLocation> findWarp(String name) {
        return locations.find(LocationRepository.Category.WARP, name);
    }

    public List<NamedLocation> findAllWarps() {
        return locations.findAll(LocationRepository.Category.WARP);
    }

    public void saveWarp(String name, Position position, UUID createdBy) {
        Optional<NamedLocation> existing = findWarp(name);
        Instant createdAt = existing.map(NamedLocation::createdAt).orElse(Instant.now());
        UUID owner = existing.map(NamedLocation::createdBy).orElse(createdBy);
        String key = existing.map(NamedLocation::key).orElse(name);
        locations.save(LocationRepository.Category.WARP, new NamedLocation(key, position, createdAt, owner));
    }

    public boolean deleteWarp(String name) {
        return locations.delete(LocationRepository.Category.WARP, name);
    }

    public boolean isWarpingDisabled(Permissible sender, String world) {
        if (sender.hasPermission(Permissions.Bypass.teleportDisabledWorlds("warp"))) {
            return false;
        }
        return configs.teleport().warps().disabledWorlds().contains(world);
    }

    public List<String> accessibleWarpNames(Permissible sender) {
        List<String> names = new ArrayList<>();
        for (NamedLocation warp : findAllWarps()) {
            if (Permissions.Access.hasWarp(sender, warp.key())) {
                names.add(warp.key());
            }
        }
        return names;
    }

    public List<String> allWarpNames() {
        return findAllWarps().stream().map(NamedLocation::key).toList();
    }

    public boolean canWarp(Permissible sender, NamedLocation warp) {
        return Permissions.Access.hasWarp(sender, warp.key()) && !isWarpingDisabled(sender, warp.position().world());
    }

    // Spawn

    public Optional<NamedLocation> findSpawn(String key) {
        return locations.find(LocationRepository.Category.SPAWN, key);
    }

    public Optional<Location> resolveSpawn(String key) {
        return findSpawn(key).map(location -> Positions.toLocation(location.position()));
    }

    public Optional<Location> defaultSpawn() {
        return resolveSpawn(LocationRepository.SPAWN_DEFAULT);
    }

    public Optional<Location> firstJoinSpawn() {
        Optional<Location> firstJoin = resolveSpawn(LocationRepository.SPAWN_FIRST_JOIN);
        return firstJoin.isPresent() ? firstJoin : defaultSpawn();
    }

    public sealed interface SpawnResolution {
        Position position();

        record Default(Position position) implements SpawnResolution {}
        record Group(String group, Position position) implements SpawnResolution {}
    }

    public Optional<SpawnResolution> spawnPositionFor(Player player) {
        String group = groups.primaryGroup(player);
        if (group != null) {
            Optional<SpawnResolution> groupSpawn = findSpawn(LocationRepository.spawnGroup(group))
                    .map(location -> new SpawnResolution.Group(group, location.position()));
            if (groupSpawn.isPresent()) {
                return groupSpawn;
            }
        }
        return findSpawn(LocationRepository.SPAWN_DEFAULT)
                .map(location -> new SpawnResolution.Default(location.position()));
    }

    public void setSpawn(String key, Position position, UUID setBy) {
        Optional<NamedLocation> existing = findSpawn(key);
        Instant createdAt = existing.map(NamedLocation::createdAt).orElse(Instant.now());
        String storedKey = existing.map(NamedLocation::key).orElse(key);
        locations.save(LocationRepository.Category.SPAWN, new NamedLocation(storedKey, position, createdAt, setBy));
    }

    public boolean deleteSpawn(String key) {
        return locations.delete(LocationRepository.Category.SPAWN, key);
    }

    public List<String> configuredSpawnGroups() {
        List<String> names = new ArrayList<>();
        for (NamedLocation location : locations.findAll(LocationRepository.Category.SPAWN)) {
            LocationRepository.groupNameFromSpawnKey(location.key()).ifPresent(names::add);
        }
        return names;
    }

    // Lifecycle

    public void handleJoin(Player player, boolean firstJoin) {
        Optional<Location> spawn = firstJoin ? firstJoinSpawn() : returningPlayerSpawn();
        spawn.ifPresent(location -> executor.teleport(player, player, location, TeleportType.SPAWN, outcome -> { }));
    }

    private Optional<Location> returningPlayerSpawn() {
        return configs.teleport().spawn().onJoin() ? defaultSpawn() : Optional.empty();
    }

    public void handleDeath(Player player) {
        if (!configs.teleport().back().saveOn().death() || executor.blocksBackInWorld(player, player.getWorld())) {
            return;
        }
        back.push(player.getUniqueId(), Positions.of(player.getLocation()));
        sendDeathBackHint(player);
    }

    private void sendDeathBackHint(Player player) {
        if (!configs.teleport().back().notifyOnDeath() || !player.hasPermission(Permissions.Command.of("back"))) {
            return;
        }
        messenger.send(player, configs.lang().get("teleport.back.death-hint"));
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        if (!configs.teleport().spawn().onDeath()) {
            return;
        }
        resolveRespawnLocation(event).ifPresent(event::setRespawnLocation);
    }

    private Optional<Location> resolveRespawnLocation(PlayerRespawnEvent event) {
        TeleportConfig.Respawn respawn = configs.teleport().respawn();
        boolean keepBed = event.isBedSpawn() && respawn.preferBed();
        boolean keepAnchor = event.isAnchorSpawn() && respawn.preferAnchor();
        if (keepBed || keepAnchor) {
            return Optional.empty();
        }
        return defaultSpawn();
    }

    public void handleQuit(UUID uuid) {
        countdown.handleQuit(uuid);
        requests.handleQuit(uuid);
        executor.handleQuit(uuid);
        back.handleQuit(uuid);
    }

    // Events feeding the countdown gate and post-teleport immunity

    public void handleMove(PlayerMoveEvent event) {
        countdown.handleMove(event);
    }

    public void handleDamage(EntityDamageEvent event) {
        executor.handleDamage(event);
        if (!event.isCancelled()) {
            countdown.handleDamage(event);
        }
    }

    public void handleCommandPreprocess(PlayerCommandPreprocessEvent event) {
        countdown.handleCommand(event.getPlayer().getUniqueId());
    }

    public void handleInteract(PlayerInteractEvent event) {
        countdown.handleInteract(event);
    }
}