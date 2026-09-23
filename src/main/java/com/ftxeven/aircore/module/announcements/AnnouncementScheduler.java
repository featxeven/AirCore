package com.ftxeven.aircore.module.announcements;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.announcements.AnnouncementsConfig.Announcement;
import com.ftxeven.aircore.module.announcements.AnnouncementsConfig.Schedule;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

final class AnnouncementScheduler {

    private final Logger logger;
    private final ConfigManager configs;
    private final Map<String, ScheduledTask> active = new ConcurrentHashMap<>();

    AnnouncementScheduler(Logger logger, ConfigManager configs) {
        this.logger = logger;
        this.configs = configs;
    }

    void schedule(String key, Announcement announcement, Runnable onFire) {
        Schedule schedule = announcement.schedule();
        switch (schedule.type()) {
            case INTERVAL -> scheduleInterval(key, schedule, onFire);
            case CRON -> scheduleCron(key, schedule, onFire);
            case STARTUP -> Scheduler.runGlobal(onFire);
            case MANUAL -> { /* only fires via AnnouncementsModule.trigger() */ }
        }
    }

    void cancel(String key) {
        ScheduledTask task = active.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    void cancelAll() {
        active.keySet().forEach(this::cancel);
    }

    private void scheduleInterval(String key, Schedule schedule, Runnable onFire) {
        long periodTicks = Math.max(1, schedule.interval()) * 20L;
        long delayTicks = Math.max(0, schedule.delay()) * 20L;
        active.put(key, Scheduler.runGlobalTimer(onFire, delayTicks, periodTicks));
    }

    private void scheduleCron(String key, Schedule schedule, Runnable onFire) {
        ZoneId zone = configs.main().formatting().timezone();
        Duration until = CronCalculator.untilNext(schedule.times(), schedule.dates(), zone, Instant.now(), warningsFor(key));
        if (until == null) {
            logger.warning("Announcement '" + key + "' has no resolvable CRON firing (check 'times'/'dates'), leaving it unscheduled");
            return;
        }
        long delayTicks = Math.max(1L, until.getSeconds() * 20L);
        active.put(key, Scheduler.runGlobalLater(() -> {
            onFire.run();
            scheduleCron(key, schedule, onFire); // one-shot -> reschedule for the following occurrence
        }, delayTicks));
    }

    private Consumer<String> warningsFor(String key) {
        return message -> logger.warning("Announcement '" + key + "': " + message);
    }
}