package com.ftxeven.aircore.module.announcements;

import com.ftxeven.aircore.core.command.CommandExecution;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.WeightedRandom;
import com.ftxeven.aircore.module.announcements.AnnouncementsConfig.Announcement;
import com.ftxeven.aircore.module.announcements.AnnouncementsConfig.AnnouncementEntry;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

final class AnnouncementEngine {

    private final Messenger messenger;
    private final PersistentBossbarCoordinator persistentBossbars;
    private final Function<UUID, Optional<PlayerProfile>> profiles;
    private final Predicate<String> hasPersistentBossbar;
    private final ConditionEvaluator conditions;

    private final Map<String, AtomicInteger> orderedPosition = new ConcurrentHashMap<>();

    AnnouncementEngine(Logger logger, Messenger messenger, PersistentBossbarCoordinator persistentBossbars,
                       Function<UUID, Optional<PlayerProfile>> profiles, Predicate<String> hasPersistentBossbar) {
        this.messenger = messenger;
        this.persistentBossbars = persistentBossbars;
        this.profiles = profiles;
        this.hasPersistentBossbar = hasPersistentBossbar;
        this.conditions = new ConditionEvaluator(logger::warning);
    }

    void resetSequenceState() {
        orderedPosition.clear();
    }

    void fire(String key, Announcement announcement, Map<String, String> placeholders) {
        List<AnnouncementEntry> entries = announcement.entries();
        if (entries.isEmpty()) {
            return;
        }

        switch (announcement.sequence()) {
            case ORDERED -> dispatch(key, announcement, nextOrdered(key, entries), placeholders);
            case RANDOM -> dispatch(key, announcement, WeightedRandom.pick(entries, AnnouncementEntry::weight), placeholders);
            case CHAIN -> fireChain(key, announcement, entries, placeholders);
        }
    }

    private void fireChain(String key, Announcement announcement, List<AnnouncementEntry> entries, Map<String, String> placeholders) {
        for (AnnouncementEntry entry : entries) {
            if (entry.offset() <= 0) {
                dispatch(key, announcement, entry, placeholders);
            } else {
                Scheduler.runGlobalLater(() -> dispatch(key, announcement, entry, placeholders), entry.offset() * 20L);
            }
        }
    }

    private AnnouncementEntry nextOrdered(String key, List<AnnouncementEntry> entries) {
        AtomicInteger position = orderedPosition.computeIfAbsent(key, k -> new AtomicInteger());
        int index = position.getAndUpdate(i -> (i + 1) % entries.size());
        return entries.get(index);
    }

    private void dispatch(String key, Announcement announcement, AnnouncementEntry entry, Map<String, String> placeholders) {
        runConsoleCommands(entry, placeholders);

        boolean hasMessage = hasContent(entry.message());
        boolean hasPlayerCommands = hasRunAs(entry.command(), MessageComponents.CommandRun.RunAs.PLAYER);

        if (hasMessage) {
            persistentBossbars.sync(key, entry.message(), placeholders, announcement.force(), announcement.conditions());
        }

        if (!hasMessage && !hasPlayerCommands) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!announcement.force() && !announcesEnabled(player.getUniqueId())) {
                continue;
            }
            Scheduler.runEntity(player, () -> deliver(player, key, announcement, entry, placeholders, hasMessage, hasPlayerCommands));
        }
    }

    private void deliver(Player player, String key, Announcement announcement, AnnouncementEntry entry,
                         Map<String, String> placeholders, boolean hasMessage, boolean hasPlayerCommands) {
        Function<String, String> resolver = Placeholders.resolver(player, placeholders);
        if (!conditions.evaluate(announcement.conditions(), resolver)) {
            return;
        }

        if (hasMessage) {
            messenger.send(player, entry.message(), placeholders, key);
        }
        if (hasPlayerCommands) {
            runPlayerCommands(player, entry, placeholders);
        }
    }

    private boolean hasRunAs(List<MessageComponents.CommandRun> commands, MessageComponents.CommandRun.RunAs runAs) {
        for (MessageComponents.CommandRun command : commands) {
            if (command.runAs() == runAs) {
                return true;
            }
        }
        return false;
    }

    private void runConsoleCommands(AnnouncementEntry entry, Map<String, String> placeholders) {
        CommandSender console = Bukkit.getConsoleSender();
        for (MessageComponents.CommandRun command : entry.command()) {
            if (command.runAs() != MessageComponents.CommandRun.RunAs.CONSOLE) {
                continue;
            }
            String resolved = Placeholders.apply(console, command.command(), placeholders);
            CommandExecution.asConsole(resolved);
        }
    }

    private void runPlayerCommands(Player player, AnnouncementEntry entry, Map<String, String> placeholders) {
        for (MessageComponents.CommandRun command : entry.command()) {
            if (command.runAs() != MessageComponents.CommandRun.RunAs.PLAYER) {
                continue;
            }
            String resolved = Placeholders.apply(player, command.command(), placeholders);
            CommandExecution.asPlayer(player, resolved);
        }
    }

    private boolean announcesEnabled(UUID uuid) {
        return profiles.apply(uuid).map(profile -> profile.toggles().announce()).orElse(true);
    }

    private boolean hasContent(MessageComponents.Bundle bundle) {
        return bundle != null && (!bundle.chat().isEmpty() || bundle.title() != null || bundle.subtitle() != null
                || bundle.actionbar() != null || bundle.bossbar() != null || bundle.sound() != null);
    }

    void restoreForJoin(Player player) {
        persistentBossbars.restore(player, this::isEligibleForRestore);
    }

    void reconcilePersistentBossbars() {
        persistentBossbars.reconcile(hasPersistentBossbar);
    }

    private boolean isEligibleForRestore(String key, Player player, boolean force, List<String> entryConditions, Map<String, String> placeholders) {
        if (!hasPersistentBossbar.test(key)) {
            return false;
        }
        if (!force && !announcesEnabled(player.getUniqueId())) {
            return false;
        }
        return conditions.evaluate(entryConditions, Placeholders.resolver(player, placeholders));
    }
}