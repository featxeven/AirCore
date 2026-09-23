package com.ftxeven.aircore.service;

import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationService {

    private final Map<UUID, Map<String, Pending<?>>> pending = new ConcurrentHashMap<>();

    public <T> void request(Player player, String type, int timeoutSeconds, T context, Runnable onExpire) {
        UUID uuid = player.getUniqueId();
        cancel(uuid, type);

        ScheduledTask timer = timeoutSeconds >= 0
                ? Scheduler.runEntityLater(player, () -> expire(uuid, type), timeoutSeconds * 20L).orElse(null)
                : null;

        if (timeoutSeconds >= 0 && timer == null) {
            return;
        }
        pending.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(type, new Pending<>(context, timer, onExpire));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> confirm(UUID player, String type) {
        Pending<?> removed = remove(player, type);
        return removed != null ? Optional.of((T) removed.context()) : Optional.empty();
    }

    public void cancel(UUID player, String type) {
        remove(player, type);
    }

    public void clear(UUID player) {
        Map<String, Pending<?>> playerPending = pending.remove(player);
        if (playerPending != null) {
            playerPending.values().forEach(Pending::cancelTimer);
        }
    }

    public void expire(UUID player, String type) {
        Pending<?> confirmation = remove(player, type);
        if (confirmation != null) {
            confirmation.onExpire().run();
        }
    }

    private Pending<?> remove(UUID player, String type) {
        Pending<?>[] removed = new Pending<?>[1];
        pending.computeIfPresent(player, (uuid, playerPending) -> {
            removed[0] = playerPending.remove(type);
            return playerPending.isEmpty() ? null : playerPending;
        });
        if (removed[0] != null) {
            removed[0].cancelTimer();
        }
        return removed[0];
    }

    private record Pending<T>(T context, @Nullable ScheduledTask timer, Runnable onExpire) {
        void cancelTimer() {
            if (timer != null) {
                timer.cancel();
            }
        }
    }
}