package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.VariableStore;
import com.ftxeven.aircore.model.Variable;
import com.ftxeven.aircore.module.announcements.AnnouncementsModule;
import com.ftxeven.aircore.module.variables.VariableCatalog.EventHook;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableDefinition;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class VariablesModule {

    public enum Order { ASC, DESC }

    public record WriteResult(boolean applied, @Nullable String value, @Nullable String reason) {
        static WriteResult rejected(@Nullable String reason) {
            return new WriteResult(false, null, reason);
        }
    }

    private final JavaPlugin plugin;
    private final VariablesConfig config;
    private final VariableStore store;
    private final VariableEngine engine;
    private final VariableIntervalScheduler scheduler;
    private final CustomEventBridge customEvents;

    public VariablesModule(JavaPlugin plugin, ConfigManager configs, Messenger messenger,
                           VariableStore store, AnnouncementsModule announcements) {
        this.plugin = plugin;
        this.config = configs.variables();
        this.store = store;
        this.engine = new VariableEngine(plugin.getLogger(), store, messenger, announcements);
        this.scheduler = new VariableIntervalScheduler(engine);
        this.customEvents = new CustomEventBridge(plugin, this);
        store.projection(engine::numeric);
    }

    // Lifecycle

    public void start() {
        load(true);
    }

    public void reload() {
        scheduler.cancelAll();
        customEvents.stop();
        load(false);
    }

    public void stop() {
        scheduler.cancelAll();
        customEvents.stop();
        store.requestFlush();
    }

    private void load(boolean startup) {
        Logger logger = plugin.getLogger();
        VariableCatalog catalog = VariableCatalog.build(config, logger::warning);
        engine.catalog(catalog);

        store.loadGlobal();
        if (startup && config.orphanCheck()) {
            store.purgeOrphans(catalog.keys()).whenComplete((purged, failure) -> {
                if (failure != null) {
                    logger.warning("Could not purge orphaned variables: " + failure.getMessage());
                } else if (purged > 0) {
                    logger.info("Purged " + purged + " orphaned variable value(s) no longer declared in config");
                }
            });
        }
        store.backfillNumeric(catalog.numericKeys()).thenAccept(filled -> {
            if (filled > 0) {
                logger.info("Backfilled ranking values for " + filled + " variable row(s)");
            }
        });

        if (startup) {
            engine.fireStartup();
        }
        scheduler.start(catalog.intervals());
        customEvents.start(config.customEvents());
    }

    // Event entry points

    public void preload(Player player) {
        store.markOnline(player.getUniqueId());
    }

    public void handleJoin(Player player) {
        store.markOnline(player.getUniqueId()).whenComplete((bucket, failure) -> {
            if (failure != null) {
                plugin.getLogger().warning("Skipping on-join variable hooks for " + player.getName() + ": " + failure.getMessage());
                return;
            }
            engine.fireEvent(EventHook.JOIN, player, Map.of());
        });
    }

    public void handleQuit(Player player) {
        engine.fireEvent(EventHook.QUIT, player, Map.of());
        store.markOffline(player.getUniqueId());
    }

    public void handleKill(Player killer) {
        engine.fireEvent(EventHook.KILL, killer, Map.of());
    }

    public void handleDeath(Player victim) {
        engine.fireEvent(EventHook.DEATH, victim, Map.of());
    }

    public void handleCustom(String eventKey, @Nullable Player player, Map<String, String> captured) {
        engine.fireCustom(eventKey, player, captured);
    }

    // Write API

    public CompletableFuture<WriteResult> write(String key, @Nullable UUID owner, VariableWrite operation) {
        return engine.write(key, owner, operation);
    }

    private static boolean accepted(CompletableFuture<WriteResult> future) {
        return !future.isDone() || future.join().applied();
    }

    public boolean set(String key, String value) { return accepted(engine.write(key, null, new VariableWrite.Set(value))); }
    public boolean add(String key, double amount) { return accepted(engine.write(key, null, new VariableWrite.Add(amount))); }
    public boolean subtract(String key, double amount) { return accepted(engine.write(key, null, new VariableWrite.Subtract(amount))); }
    public boolean reset(String key) { return accepted(engine.write(key, null, new VariableWrite.Reset())); }
    public boolean toggle(String key) { return accepted(engine.write(key, null, new VariableWrite.Toggle())); }

    public boolean set(UUID owner, String key, String value) { return accepted(engine.write(key, owner, new VariableWrite.Set(value))); }
    public boolean add(UUID owner, String key, double amount) { return accepted(engine.write(key, owner, new VariableWrite.Add(amount))); }
    public boolean subtract(UUID owner, String key, double amount) { return accepted(engine.write(key, owner, new VariableWrite.Subtract(amount))); }
    public boolean reset(UUID owner, String key) { return accepted(engine.write(key, owner, new VariableWrite.Reset())); }
    public boolean toggle(UUID owner, String key) { return accepted(engine.write(key, owner, new VariableWrite.Toggle())); }

    // Read API. Effective values: the stored value or the configured default.

    public Optional<String> get(String key) {
        return engine.get(key, null);
    }

    // empty when the owner's data isn't resident, use getAsync for offline players
    public Optional<String> get(UUID owner, String key) {
        return engine.get(key, owner);
    }

    public CompletableFuture<Optional<String>> getAsync(UUID owner, String key) {
        return engine.getAsync(key, owner);
    }

    public List<Variable> list() {
        return engine.listGlobal();
    }

    // empty when the owner's data isn't resident, use listAsync for offline players
    public List<Variable> list(UUID owner) {
        return engine.listForPlayer(owner);
    }

    public CompletableFuture<List<Variable>> listAsync(UUID owner) {
        return engine.listAsync(owner);
    }

    // PlaceholderAPI bridge

    public @Nullable String resolve(@Nullable OfflinePlayer viewer, String params) {
        return engine.resolve(viewer, params);
    }

    // Leaderboards. Flushes pending writes first, then ranks straight from the DB, so a ranking
    // never misses a write that's still only sitting in memory
    public CompletableFuture<List<Variable>> top(String key, Order order, int limit, double minValue) {
        return store.top(key, order == Order.DESC, limit, minValue);
    }

    public CompletableFuture<Integer> deleteAll(UUID owner) {
        return store.deleteAll(owner);
    }

    // Introspection

    public boolean has(String key) {
        return config.variables().containsKey(key);
    }

    public Set<String> keys() {
        return config.variables().keySet();
    }

    public Optional<VariableDefinition> definition(String key) {
        return Optional.ofNullable(config.variables().get(key));
    }
}