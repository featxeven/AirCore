package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class CacheManager {

    private static final long COOLDOWN_PURGE_INTERVAL_MINUTES = 360L;
    private static final long RESIDENT_SWEEP_INTERVAL_MINUTES = 1L;

    private static final Duration TOTAL_BALANCE_TTL = Duration.ofSeconds(30);
    private static final Duration IDENTITY_TTL = Duration.ofMinutes(15);
    private static final Duration OFFLINE_PROFILE_TTL = Duration.ofMinutes(5);

    private final WriteBehind writes;
    private final PlayerCache players;
    private final HomeCache homes;
    private final VariableStore variables;
    private final LocationCache locations;
    private final KitCache kits;
    private final BlockCache blocks;
    private final CooldownCache cooldowns;

    public CacheManager(DatabaseManager database, Logger logger) {
        writes = new WriteBehind("AirCore-Writes", logger);

        Predicate<UUID> online = uuid -> Bukkit.getPlayer(uuid) != null;
        players = new PlayerCache(database.players(), TOTAL_BALANCE_TTL, IDENTITY_TTL, OFFLINE_PROFILE_TTL, online);
        homes = new HomeCache(database.homes(), writes, online, logger);
        variables = new VariableStore(database.variables(), logger, VariableStore.Settings.DEFAULT);
        locations = new LocationCache(database.locations(), writes, Duration.ofMinutes(30));
        kits = new KitCache(database.kits(), writes, Duration.ofMinutes(30));
        blocks = new BlockCache(database.blocks(), writes, online, logger);
        cooldowns = new CooldownCache(database.cooldowns(), writes);
    }

    public WriteBehind writes() { return writes; }
    public PlayerCache players() { return players; }
    public HomeCache homes() { return homes; }
    public VariableStore variables() { return variables; }
    public LocationCache locations() { return locations; }
    public KitCache kits() { return kits; }
    public BlockCache blocks() { return blocks; }
    public CooldownCache cooldowns() { return cooldowns; }

    public void startMaintenance(Logger logger) {
        Scheduler.runAsyncTimer(() -> {
            try {
                int purged = cooldowns.purgeExpired(Instant.now());
                if (purged > 0) {
                    logger.info("Purged " + purged + " expired cooldown row(s)");
                }
            } catch (RuntimeException e) {
                logger.warning("Could not purge expired cooldowns: " + e.getMessage());
            }
        }, 1L, COOLDOWN_PURGE_INTERVAL_MINUTES, TimeUnit.MINUTES);

        Scheduler.runAsyncTimer(() -> {
            try {
                players.evictExpired();
                homes.evictExpired();
                blocks.evictExpired();
            } catch (RuntimeException e) {
                logger.warning("Could not sweep resident caches: " + e.getMessage());
            }
        }, RESIDENT_SWEEP_INTERVAL_MINUTES, RESIDENT_SWEEP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void close() {
        writes.close();
        variables.close();
    }
}