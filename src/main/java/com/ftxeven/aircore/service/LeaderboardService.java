package com.ftxeven.aircore.service;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.LeaderboardsConfig;
import com.ftxeven.aircore.config.LeaderboardsConfig.Leaderboard;
import com.ftxeven.aircore.config.LeaderboardsConfig.Source;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.model.Variable;
import com.ftxeven.aircore.module.variables.VariablesConfig;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableDefinition;
import com.ftxeven.aircore.module.variables.VariablesModule;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LeaderboardService {

    // how long a refresh waits on the variables io thread before giving up, so a stuck query can't freeze a board
    private static final long RANK_TIMEOUT_SECONDS = 30L;

    // value is the stored text of a variable row, null for balance rows (formatted from score at render time)
    public record Entry(int rank, PlayerRepository.Identity holder, double score, @Nullable String value) {}

    // computedAt stays null until the first successful refresh
    public record Snapshot(List<Entry> entries, Map<UUID, Entry> byHolder, @Nullable Instant computedAt) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), null);

        private static Snapshot of(List<Entry> entries) {
            Map<UUID, Entry> byHolder = HashMap.newHashMap(entries.size());
            for (Entry entry : entries) {
                byHolder.put(entry.holder().uuid(), entry);
            }
            return new Snapshot(List.copyOf(entries), Collections.unmodifiableMap(byHolder), Instant.now());
        }

        public int size() {
            return entries.size();
        }

        public boolean loaded() {
            return computedAt != null;
        }
    }

    private static final class Board {
        private final Leaderboard definition;
        private final AtomicReference<CompletableFuture<Snapshot>> running = new AtomicReference<>();
        private volatile Snapshot snapshot;
        private ScheduledTask task;

        private Board(Leaderboard definition, Snapshot snapshot) {
            this.definition = definition;
            this.snapshot = snapshot;
        }

        // refresh-interval doesn't change what is ranked, so a snapshot survives it
        private boolean ranksLike(Leaderboard other) {
            return definition.source().equals(other.source())
                    && definition.order() == other.order()
                    && definition.maxEntries() == other.maxEntries()
                    && definition.minValue() == other.minValue();
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private final Logger logger;
    private final ConfigManager configs;
    private final PlayerService players;
    private final Supplier<VariablesModule> variables;

    private volatile Map<String, Board> boards = Map.of();

    public LeaderboardService(Logger logger, ConfigManager configs, PlayerService players, Supplier<VariablesModule> variables) {
        this.logger = logger;
        this.configs = configs;
        this.players = players;
        this.variables = variables;
    }

    // Lifecycle

    public synchronized void load() {
        Map<String, Board> previous = boards;
        previous.values().forEach(Board::cancel);

        Map<String, Board> next = new HashMap<>();
        for (Leaderboard definition : configs.leaderboards().leaderboards().values()) {
            if (!usable(definition)) {
                continue;
            }
            Board old = previous.get(definition.id());
            Snapshot carried = old != null && old.ranksLike(definition) ? old.snapshot : Snapshot.EMPTY;
            next.put(definition.id(), new Board(definition, carried));
        }
        boards = Map.copyOf(next);
        boards.values().forEach(this::schedule);
    }

    private void schedule(Board board) {
        board.task = Scheduler.runAsyncTimer(() -> refresh(board), 1L, board.definition.refreshInterval(), TimeUnit.SECONDS);
    }

    private boolean usable(Leaderboard board) {
        if (!(board.source() instanceof Source.Variable source)) {
            return true;
        }
        String where = "Leaderboard '" + board.id() + "' in " + configs.leaderboards().fileName() + " uses variable '" + source.id() + "', but ";
        VariablesModule module = variables.get();
        if (module == null) {
            logger.warning(where + "the variables module is not loaded, skipping");
            return false;
        }
        Optional<VariableDefinition> definition = module.definition(source.id());
        if (definition.isEmpty()) {
            logger.warning(where + "it is not declared in modules/variables, skipping");
            return false;
        }
        if (definition.get().scope() != VariablesConfig.VariableScope.PLAYER) {
            logger.warning(where + "it is global, only player variables have holders to rank, skipping");
            return false;
        }
        VariablesConfig.VariableType type = definition.get().type();
        if (type != VariablesConfig.VariableType.INTEGER && type != VariablesConfig.VariableType.DOUBLE) {
            logger.warning(where + "it is not numeric, skipping");
            return false;
        }
        return true;
    }

    // Reads

    public Snapshot snapshot(String id) {
        Board board = boards.get(id);
        return board != null ? board.snapshot : Snapshot.EMPTY;
    }

    public PageResult<Entry> page(String id, int page, int pageSize) {
        return PageResult.of(snapshot(id).entries(), page, pageSize);
    }

    public Optional<Entry> entryOf(String id, UUID holder) {
        return Optional.ofNullable(snapshot(id).byHolder().get(holder));
    }

    public OptionalInt rankOf(String id, UUID holder) {
        return entryOf(id, holder).map(entry -> OptionalInt.of(entry.rank())).orElseGet(OptionalInt::empty);
    }

    // Refreshing

    public CompletableFuture<Snapshot> refresh(String id) {
        Board board = boards.get(id);
        return board != null ? refresh(board) : CompletableFuture.completedFuture(Snapshot.EMPTY);
    }

    private CompletableFuture<Snapshot> refresh(Board board) {
        CompletableFuture<Snapshot> fresh = new CompletableFuture<>();
        CompletableFuture<Snapshot> inFlight = board.running.compareAndExchange(null, fresh);
        if (inFlight != null) {
            return inFlight;
        }

        Scheduler.runAsync(() -> {
            try {
                Snapshot ranked = build(board.definition);
                if (owns(board)) {
                    board.snapshot = ranked;
                }
            } catch (RuntimeException failure) {
                if (owns(board)) {
                    logger.log(Level.WARNING, "Could not refresh leaderboard '" + board.definition.id()
                            + "', keeping the previous ranking", unwrap(failure));
                }
            } finally {
                board.running.set(null);
                fresh.complete(board.snapshot);
            }
        });
        return fresh;
    }

    private boolean owns(Board board) {
        return boards.get(board.definition.id()) == board;
    }

    private Snapshot build(Leaderboard board) {
        boolean descending = board.order() == LeaderboardsConfig.Order.DESC;
        return Snapshot.of(switch (board.source()) {
            case Source.Balance ignored -> rankBalances(board, descending);
            case Source.Variable variable -> rankVariable(variable, board, descending);
        });
    }

    private List<Entry> rankBalances(Leaderboard board, boolean descending) {
        List<PlayerRepository.BalanceEntry> rows = players.topBalances(descending, board.maxEntries(), board.minValue());
        List<Entry> entries = new ArrayList<>(rows.size());
        for (PlayerRepository.BalanceEntry row : rows) {
            entries.add(new Entry(entries.size() + 1, row.holder(), row.balance(), null));
        }
        return entries;
    }

    private List<Entry> rankVariable(Source.Variable source, Leaderboard board, boolean descending) {
        VariablesModule module = variables.get();
        if (module == null) {
            throw new IllegalStateException("Variables module is not loaded");
        }
        VariablesModule.Order order = descending ? VariablesModule.Order.DESC : VariablesModule.Order.ASC;
        List<Variable> rows = module.top(source.id(), order, board.maxEntries(), board.minValue())
                .orTimeout(RANK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join()
                .stream()
                .filter(row -> row.owner() != null && row.score() != null)
                .toList();

        Map<UUID, PlayerRepository.Identity> identities = players.identities(rows.stream().map(Variable::owner).toList());
        List<Entry> entries = new ArrayList<>(rows.size());
        for (Variable row : rows) {
            PlayerRepository.Identity holder = identities.get(row.owner());
            if (holder != null) {
                entries.add(new Entry(entries.size() + 1, holder, row.score(), row.value()));
            }
        }
        return entries;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}