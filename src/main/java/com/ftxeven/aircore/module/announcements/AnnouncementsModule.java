package com.ftxeven.aircore.module.announcements;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.announcements.AnnouncementsConfig.Announcement;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.database.repository.PersistentBossbarRepository;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class AnnouncementsModule {

    private final Logger logger;
    private final AnnouncementsConfig config;
    private final AnnouncementScheduler scheduler;
    private final AnnouncementEngine engine;

    public AnnouncementsModule(JavaPlugin plugin, ConfigManager configs, Messenger messenger,
                               PersistentBossbarRepository persistentBossbar, ServiceManager services) {
        this.logger = plugin.getLogger();
        this.config = configs.announcements();
        this.scheduler = new AnnouncementScheduler(logger, configs);
        PersistentBossbarCoordinator persistentBossbars = new PersistentBossbarCoordinator(logger, messenger, persistentBossbar);
        this.engine = new AnnouncementEngine(logger, messenger, persistentBossbars, services.players()::peek, config::hasActivePersistentBossbar);
    }

    // Lifecycle

    public void handleJoin(Player player) {
        engine.restoreForJoin(player);
    }

    public void start() {
        engine.reconcilePersistentBossbars();
        config.announcements().forEach(this::scheduleIfEnabled);
    }

    public void reload() {
        scheduler.cancelAll();
        engine.resetSequenceState();
        start();
    }

    public void stop() {
        scheduler.cancelAll();
    }

    // API

    public boolean trigger(String key) {
        return trigger(key, List.of());
    }

    public boolean trigger(String key, List<String> args) {
        Announcement announcement = config.announcements().get(key);
        if (announcement == null) {
            if (config.disabledByFile().contains(key)) {
                logger.info("Announcement '" + key + "' is disabled via its config file, ignoring trigger");
            } else {
                logger.warning("Attempted to trigger unknown announcement '" + key + "'");
            }
            return false;
        }
        if (!announcement.enabled()) {
            return false;
        }

        Map<String, String> placeholders = argPlaceholders(args);

        Scheduler.runGlobal(() -> engine.fire(key, announcement, placeholders));
        return true;
    }

    public boolean isEnabled(String key) {
        return config.isActive(key);
    }

    public boolean isDisabledByFile(String key) {
        return config.disabledByFile().contains(key);
    }

    public Set<String> keys() {
        return config.keySet();
    }

    // Internal

    private void scheduleIfEnabled(String key, Announcement announcement) {
        if (announcement.enabled()) {
            scheduler.schedule(key, announcement, () -> engine.fire(key, announcement, Map.of()));
        }
    }

    private Map<String, String> argPlaceholders(List<String> args) {
        if (args.isEmpty()) {
            return Map.of();
        }
        Map<String, String> placeholders = new LinkedHashMap<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            placeholders.put("announcement_arg_" + (i + 1), args.get(i));
        }
        return placeholders;
    }
}