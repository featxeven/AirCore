package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.database.repository.VariableRepository;
import com.ftxeven.aircore.model.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VariableStore {

    public record Settings(long flushIntervalMillis, long residentIdleMillis) {
        public static final Settings DEFAULT = new Settings(1_000L, TimeUnit.MINUTES.toMillis(3));
    }

    // maps a stored value to its numeric ranking column; null for non-numeric variables
    @FunctionalInterface
    public interface Projection {
        @Nullable Double numeric(String key, String value);
    }

    private static final int MAX_BATCH = 5_000;
    private static final long SWEEP_SECONDS = 30L;
    private static final long LOG_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final VariableRepository repository;
    private final Logger logger;
    private final Settings settings;
    private final ScheduledThreadPoolExecutor io;

    private final Queue<VariableBucket> flushQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, VariableBucket> players = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<VariableBucket>> loading = new ConcurrentHashMap<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushRequested = new AtomicBoolean();

    private volatile VariableBucket global;
    private volatile Projection projection = (key, value) -> null;
    private volatile boolean closed;
    private boolean globalLoaded; // guarded by this
    private long lastFailureLog = Long.MIN_VALUE; // io thread only

    public VariableStore(VariableRepository repository, Logger logger, Settings settings) {
        this.repository = repository;
        this.logger = logger;
        this.settings = settings;
        this.global = new VariableBucket(VariableRepository.GLOBAL_OWNER, Map.of(), flushQueue);
        this.io = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "AirCore-Variables-IO");
            thread.setDaemon(true);
            return thread;
        });
        io.setRemoveOnCancelPolicy(true);
        io.scheduleWithFixedDelay(this::flushQuietly, settings.flushIntervalMillis(), settings.flushIntervalMillis(), TimeUnit.MILLISECONDS);
        io.scheduleWithFixedDelay(this::sweepQuietly, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS);
    }

    public void projection(Projection projection) {
        this.projection = projection;
    }

    // Global scope

    // idempotent so module reloads keep the live state
    public synchronized void loadGlobal() {
        if (globalLoaded) {
            return;
        }
        global = new VariableBucket(VariableRepository.GLOBAL_OWNER, repository.loadGlobal(), flushQueue);
        globalLoaded = true;
    }

    public VariableBucket global() {
        return global;
    }

    // Player scope

    // the resident bucket, or null when not loaded (yet)
    public @Nullable VariableBucket resident(UUID uuid) {
        VariableBucket bucket = players.get(uuid);
        if (bucket != null) {
            bucket.touch();
        }
        return bucket;
    }

    // resident bucket, loading it on the io thread when needed
    public CompletableFuture<VariableBucket> acquire(UUID uuid) {
        VariableBucket bucket = resident(uuid);
        if (bucket != null) {
            return CompletableFuture.completedFuture(bucket);
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Variable store is closed"));
        }
        return loading.computeIfAbsent(uuid, this::startLoad);
    }

    private CompletableFuture<VariableBucket> startLoad(UUID uuid) {
        CompletableFuture<VariableBucket> future = new CompletableFuture<>();
        try {
            io.execute(() -> {
                try {
                    VariableBucket bucket = players.get(uuid);
                    if (bucket == null) {
                        bucket = new VariableBucket(uuid.toString(), repository.loadPlayer(uuid), flushQueue);
                        players.put(uuid, bucket);
                    }
                    loading.remove(uuid, future);
                    future.complete(bucket);
                } catch (Throwable t) {
                    // never fall back to an empty bucket
                    loading.remove(uuid, future);
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<VariableBucket> markOnline(UUID uuid) {
        online.add(uuid);
        return acquire(uuid);
    }

    public void markOffline(UUID uuid) {
        online.remove(uuid);
        VariableBucket bucket = players.get(uuid);
        if (bucket != null) {
            bucket.touchNow(); // start the idle grace period from the moment they left
        }
        requestFlush();
    }

    // Persistence

    public void requestFlush() {
        if (closed || !flushRequested.compareAndSet(false, true)) {
            return;
        }
        try {
            io.execute(() -> {
                flushRequested.set(false);
                flushQuietly();
            });
        } catch (RejectedExecutionException e) {
            flushRequested.set(false);
        }
    }

    // io thread only. Returns false when a batch failed (its keys are re-queued for the next flush)
    private boolean flush() {
        List<VariableRepository.Change> changes = new ArrayList<>();
        List<VariableBucket> sources = new ArrayList<>();
        Projection projection = this.projection;
        while (true) {
            changes.clear();
            sources.clear();
            VariableBucket bucket;
            while (changes.size() < MAX_BATCH && (bucket = flushQueue.poll()) != null) {
                bucket.drain(projection, changes, sources);
            }
            if (changes.isEmpty()) {
                return true;
            }
            try {
                repository.apply(changes);
            } catch (RuntimeException e) {
                for (int i = 0; i < changes.size(); i++) {
                    sources.get(i).remark(changes.get(i).key()); // retry re-reads the latest value
                }
                logFailure(e, changes.size());
                return false;
            }
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Variable flush crashed", t);
        }
    }

    private void logFailure(RuntimeException e, int count) {
        long now = System.nanoTime();
        if (lastFailureLog != Long.MIN_VALUE && now - lastFailureLog < LOG_THROTTLE_NANOS) {
            return;
        }
        lastFailureLog = now;
        logger.log(Level.SEVERE, "Could not persist " + count + " variable change(s), retrying on the next flush", e);
    }

    private void sweepQuietly() {
        try {
            long idleNanos = TimeUnit.MILLISECONDS.toNanos(settings.residentIdleMillis());
            players.forEach((uuid, bucket) -> {
                if (online.contains(uuid) || bucket.hasPendingWrites() || bucket.idleNanos() < idleNanos) {
                    bucket.cancelEviction();
                } else if (bucket.confirmEviction()) {
                    players.remove(uuid, bucket);
                }
            });
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Variable sweep crashed", t);
        }
    }

    // Maintenance & queries

    public CompletableFuture<Integer> purgeOrphans(Set<String> known) {
        Set<String> keep = Set.copyOf(known);
        return CompletableFuture.supplyAsync(() -> {
            flush();
            int purged = repository.purgeOrphaned(keep);
            global.retain(keep);
            players.values().forEach(bucket -> bucket.retain(keep));
            return purged;
        }, io);
    }

    public CompletableFuture<Integer> backfillNumeric(Iterable<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            int total = 0;
            for (String key : keys) {
                try {
                    total += repository.backfillNumeric(key);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Could not backfill numeric values for variable '" + key + "'", e);
                }
            }
            return total;
        }, io);
    }

    // flushes first, so a ranking never misses writes that are still waiting in memory
    public CompletableFuture<List<Variable>> top(String key, boolean descending, int limit, double minValue) {
        return CompletableFuture.supplyAsync(() -> {
            flush();
            return repository.top(key, descending, limit, minValue);
        }, io);
    }

    public CompletableFuture<Integer> deleteAll(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            VariableBucket bucket = players.remove(uuid);
            if (bucket != null) {
                bucket.discard(); // pending writes of the dropped bucket must not resurrect rows
            }
            flush();
            return repository.deleteAll(uuid);
        }, io);
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Callable<Boolean> finalFlush = this::flush;
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (io.submit(finalFlush).get(30, TimeUnit.SECONDS)) {
                    break;
                }
                Thread.sleep(250L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not flush variables on shutdown", e);
        }
        io.shutdownNow();
    }
}