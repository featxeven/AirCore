package com.ftxeven.aircore.module.teleport.countdown;

import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.TeleportConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class CountdownGate {

    public sealed interface CancelReason {
        record Moved() implements CancelReason {}
        record Damaged() implements CancelReason {}
        record UsedCommand() implements CancelReason {}
        record Interacted() implements CancelReason {}
        record Disconnected() implements CancelReason {}
    }

    public interface Session {
        default void onTick(int secondsRemaining) { }
        default void onCancel(CancelReason reason) { }
        void onComplete();
    }

    private record ActiveCountdown(Position anchor, Session session, @Nullable ScheduledTask task) {}

    private final Supplier<TeleportConfig> config;
    private final Map<UUID, ActiveCountdown> active = new ConcurrentHashMap<>();

    public CountdownGate(Supplier<TeleportConfig> config) {
        this.config = config;
    }

    public boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }

    public void start(Player player, CommandSender initiator, TeleportType type, Session session) {
        cancelQuietly(player.getUniqueId());
        Scheduler.runEntity(player, () -> beginCountdown(player, initiator, type, session));
    }

    private void beginCountdown(Player player, CommandSender initiator, TeleportType type, Session session) {
        UUID uuid = player.getUniqueId();
        boolean exempt = config.get().general().isRestrictionExempt(type);
        int seconds = config.get().countdown().resolveDuration(player.getWorld().getName(), type);

        if (exempt || seconds <= 0
                || player.hasPermission(Permissions.Bypass.COUNTDOWN)
                || initiator.hasPermission(Permissions.Bypass.COUNTDOWN)) {
            session.onComplete();
            return;
        }

        boolean repeat = config.get().countdown().repeatMessage();
        AtomicInteger remaining = new AtomicInteger(seconds);
        AtomicReference<ScheduledTask> task = new AtomicReference<>();
        AtomicReference<ActiveCountdown> self = new AtomicReference<>();

        ScheduledTask scheduled = Scheduler.runEntityTimer(player, () -> {
            ActiveCountdown mine = self.get();
            if (mine == null || active.get(uuid) != mine) {
                cancelTask(task);
                return;
            }
            int left = remaining.decrementAndGet();
            if (left <= 0) {
                active.remove(uuid, mine);
                cancelTask(task);
                session.onComplete();
                return;
            }
            if (repeat) {
                session.onTick(left);
            }
        }, 20L, 20L).orElse(null);

        task.set(scheduled);

        ActiveCountdown countdown = new ActiveCountdown(Positions.of(player.getLocation()), session, scheduled);
        self.set(countdown);

        ActiveCountdown previous = active.put(uuid, countdown);
        if (previous != null) {
            stop(previous);
        }
        session.onTick(seconds);
    }

    private static void cancelTask(AtomicReference<ScheduledTask> holder) {
        ScheduledTask task = holder.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancel(UUID uuid, CancelReason reason) {
        ActiveCountdown countdown = active.remove(uuid);
        if (countdown == null) {
            return;
        }
        stop(countdown);
        countdown.session().onCancel(reason);
    }

    private void cancelQuietly(UUID uuid) {
        ActiveCountdown countdown = active.remove(uuid);
        if (countdown != null) {
            stop(countdown);
        }
    }

    private void stop(ActiveCountdown countdown) {
        if (countdown.task() != null) {
            countdown.task().cancel();
        }
    }

    public void handleQuit(UUID uuid) {
        cancel(uuid, new CancelReason.Disconnected());
    }

    // Cancel-on triggers

    public void handleMove(PlayerMoveEvent event) {
        if (active.isEmpty() || !config.get().countdown().cancelOn().move()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveCountdown countdown = active.get(uuid);
        if (countdown == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (!countdown.anchor().world().equals(to.getWorld().getName())) {
            cancel(uuid, new CancelReason.Moved());
            return;
        }
        double threshold = config.get().countdown().cancelOn().moveThreshold();
        if (Positions.distanceSquared(countdown.anchor(), Positions.of(to)) >= threshold * threshold) {
            cancel(uuid, new CancelReason.Moved());
        }
    }

    public void handleDamage(EntityDamageEvent event) {
        if (active.isEmpty() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!config.get().countdown().cancelOn().damage()) {
            return;
        }
        if (active.containsKey(player.getUniqueId())) {
            cancel(player.getUniqueId(), new CancelReason.Damaged());
        }
    }

    public void handleCommand(UUID uuid) {
        if (active.isEmpty() || !config.get().countdown().cancelOn().command()) {
            return;
        }
        if (active.containsKey(uuid)) {
            cancel(uuid, new CancelReason.UsedCommand());
        }
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (active.isEmpty() || !config.get().countdown().cancelOn().interact()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (active.containsKey(uuid)) {
            cancel(uuid, new CancelReason.Interacted());
        }
    }
}