package com.ftxeven.aircore.module.announcements;

import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.core.message.BossbarStyles;
import com.ftxeven.aircore.core.message.MessageTag;
import com.ftxeven.aircore.database.repository.PersistentBossbarRepository;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

final class PersistentBossbarCoordinator {

    private final Logger logger;
    private final Messenger messenger;
    private final PersistentBossbarRepository repository;

    PersistentBossbarCoordinator(Logger logger, Messenger messenger, PersistentBossbarRepository repository) {
        this.logger = logger;
        this.messenger = messenger;
        this.repository = repository;
    }

    void sync(String key, MessageComponents.Bundle bundle, Map<String, String> placeholders, boolean force, List<String> conditions) {
        if (bundle == null || bundle.bossbar() == null) {
            return;
        }
        switch (bundle.bossbar()) {
            case MessageComponents.BossbarAction.Clear ignored -> Scheduler.runAsync(() -> repository.clear(key));
            case MessageComponents.BossbarAction.Show show -> {
                if (show.persist()) {
                    Scheduler.runAsync(() -> repository.save(new PersistentBossbarRepository.State(
                            key, show.text(), show.duration(), show.color(), show.overlay(),
                            show.countdown(), show.progress(), System.currentTimeMillis(), placeholders,
                            show.syncOnJoin(), force, conditions
                    )));
                } else {
                    Scheduler.runAsync(() -> repository.clear(key));
                }
            }
        }
    }

    void reconcile(Predicate<String> hasActivePersistentBossbar) {
        Scheduler.runAsync(() -> {
            for (PersistentBossbarRepository.State state : repository.findAll()) {
                if (!hasActivePersistentBossbar.test(state.key())) {
                    repository.clear(state.key());
                    logger.info("Cleared stale persistent bossbar '" + state.key() + "' (no longer declared active in config)");
                }
            }
        });
    }

    @FunctionalInterface
    interface Gate {
        boolean isEligible(String key, Player player, boolean force, List<String> conditions, Map<String, String> placeholders);
    }

    void restore(Player player, Gate gate) {
        Scheduler.runAsync(() -> {
            List<PersistentBossbarRepository.State> stored = repository.findAll();
            if (stored.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            List<PersistentBossbarRepository.State> live = new ArrayList<>(stored.size());
            List<String> expiredKeys = new ArrayList<>();
            for (PersistentBossbarRepository.State state : stored) {
                if (!state.syncOnJoin()) {
                    continue;
                }
                if (Timing.of(state, now).expired()) {
                    expiredKeys.add(state.key()); // ran out while nobody was around to see it end
                } else {
                    live.add(state);
                }
            }
            expiredKeys.forEach(repository::clear);

            if (!live.isEmpty()) {
                Scheduler.runEntity(player, () -> show(player, live, gate));
            }
        });
    }

    // player's own thread
    private void show(Player player, List<PersistentBossbarRepository.State> states, Gate gate) {
        if (!player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PersistentBossbarRepository.State state : states) {
            Timing timing = Timing.of(state, now);
            if (timing.expired()) {
                continue; // ran out in the time since it was read; the next restore/reconcile clears the row
            }
            if (!gate.isEligible(state.key(), player, state.force(), state.conditions(), state.placeholders())) {
                continue;
            }

            String text = Placeholders.apply(player, state.text(), state.placeholders());
            messenger.renderBossBar(player, new MessageTag.BossBar(
                    state.key(),
                    text,
                    timing.remainingTicks(),
                    BossbarStyles.color(state.color(), logger),
                    BossbarStyles.overlay(state.overlay(), logger),
                    timing.progress(state.initialProgress()),
                    state.countdown() && !timing.infinite()
            ));
        }
    }

    // where a stored bar is in its life at a given moment
    private record Timing(boolean infinite, long totalTicks, long elapsedTicks) {

        static Timing of(PersistentBossbarRepository.State state, long nowMillis) {
            return new Timing(
                    state.durationSeconds() <= 0,
                    state.durationSeconds() * 20L,
                    Math.max(0L, (nowMillis - state.startedAtEpochMillis()) / 50L));
        }

        boolean expired() {
            return !infinite && elapsedTicks >= totalTicks;
        }

        long remainingTicks() {
            return infinite ? 0L : totalTicks - elapsedTicks;
        }

        float progress(double initialProgress) {
            if (infinite) {
                return (float) initialProgress;
            }
            return (float) Math.max(0.0, initialProgress * (1.0 - (double) elapsedTicks / totalTicks));
        }
    }
}