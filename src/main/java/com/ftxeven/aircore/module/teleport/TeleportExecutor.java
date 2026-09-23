package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.back.BackLocationTracker;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Cooldowns;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TeleportExecutor {

    public sealed interface Outcome {
        record Teleported() implements Outcome {}
        record DisabledWorld() implements Outcome {}
        record UnsafeDestination() implements Outcome {}
        record DestinationOffline() implements Outcome {}
    }

    private final Supplier<TeleportConfig> config;
    private final BackLocationTracker backLocations;
    private final Cooldowns<UUID> immunity = new Cooldowns<>();

    public TeleportExecutor(Supplier<TeleportConfig> config, BackLocationTracker backLocations) {
        this.config = config;
        this.backLocations = backLocations;
    }

    public boolean isDisabledWorld(Player player, CommandSender initiator, World world) {
        String permission = Permissions.Bypass.teleportDisabledWorlds("general");
        if (player.hasPermission(permission) || initiator.hasPermission(permission)) {
            return false;
        }
        return config.get().general().disabledWorlds().contains(world.getName());
    }

    public boolean blocksDisabledWorld(Player player, CommandSender initiator, World world, TeleportType type) {
        return !config.get().general().isRestrictionExempt(type) && isDisabledWorld(player, initiator, world);
    }

    public void teleport(Player player, CommandSender initiator, Location destination, TeleportType type, Consumer<Outcome> callback) {
        if (blocksDisabledWorld(player, initiator, destination.getWorld(), type)) {
            callback.accept(new Outcome.DisabledWorld());
            return;
        }
        resolveDestination(destination, type, resolved -> {
            if (resolved.isEmpty()) {
                callback.accept(new Outcome.UnsafeDestination());
                return;
            }
            finish(player, resolved.get(), type);
            callback.accept(new Outcome.Teleported());
        });
    }

    public void resolveDestination(Location destination, TeleportType type, Consumer<Optional<Location>> callback) {
        boolean exempt = config.get().general().isRestrictionExempt(type);
        if (!config.get().general().safeLanding() || exempt) {
            callback.accept(Optional.of(prepare(destination)));
            return;
        }

        int radius = config.get().general().safeLandingRadius();
        destination.getWorld().getChunkAtAsync(destination)
                .thenRun(() -> Scheduler.runRegion(destination, () ->
                        callback.accept(SafeLocationFinder.findNearestSafe(prepare(destination), radius))))
                .exceptionally(error -> {
                    callback.accept(Optional.empty());
                    return null;
                });
    }

    private Location prepare(Location destination) {
        return config.get().general().teleportToCenter() ? Positions.toBlockCenter(destination) : destination.clone();
    }

    public void finish(Player player, Location resolved, TeleportType type) {
        Runnable action = () -> {
            recordBackLocation(player, type);
            player.teleportAsync(resolved, PlayerTeleportEvent.TeleportCause.PLUGIN);
            grantImmunity(player);
        };
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
        } else {
            Scheduler.runEntity(player, action);
        }
    }

    private void recordBackLocation(Player player, TeleportType type) {
        if (shouldSaveBack(type) && !blocksBackInWorld(player, player.getWorld())) {
            backLocations.push(player.getUniqueId(), Positions.of(player.getLocation()));
        }
    }

    private boolean shouldSaveBack(TeleportType type) {
        return config.get().back().saveOn().teleport() && type != TeleportType.BACK;
    }

    public boolean blocksBackInWorld(Player player, World world) {
        if (player.hasPermission(Permissions.Bypass.teleportDisabledWorlds("back"))) {
            return false;
        }
        return config.get().back().disabledWorlds().contains(world.getName());
    }

    // Post-teleport immunity

    private void grantImmunity(Player player) {
        if (config.get().postTeleport().immunityDuration() > 0) {
            immunity.hit(player.getUniqueId());
        }
    }

    public boolean isImmune(UUID uuid) {
        int duration = config.get().postTeleport().immunityDuration();
        return duration > 0 && immunity.remainingSeconds(uuid, duration) > 0;
    }

    public void handleDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isImmune(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public void handleQuit(UUID uuid) {
        immunity.clear(uuid);
    }
}