package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.query.HomeFacetCounts;
import com.ftxeven.aircore.database.query.HomeQuery;
import com.ftxeven.aircore.database.query.HomeSort;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.database.repository.HomeRepository;
import com.ftxeven.aircore.model.Home;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

public final class HomeCache {

    private record RowKey(UUID owner, String name) {}

    private final HomeRepository repository;
    private final WriteBehind writes;
    private final ResidentStore<OwnerHomes> resident;

    public HomeCache(HomeRepository repository, WriteBehind writes, Predicate<UUID> isOnline, Logger logger) {
        this.repository = repository;
        this.writes = writes;
        this.resident = new ResidentStore<>("homes", this::load, isOnline, Duration.ofMinutes(5), logger);
    }

    // Residency

    /** Warms this player's homes off-thread. Call on join so the first command never has to wait. */
    public CompletableFuture<Void> warm(UUID owner) {
        return resident.load(owner);
    }

    public void release(UUID owner) {
        resident.release(owner);
    }

    /** Hard drop for when the database changed behind the cache's back (migrations). */
    public void invalidate(UUID owner) {
        resident.invalidate(owner);
    }

    public void evictExpired() {
        resident.evictExpired();
    }

    private OwnerHomes load(UUID owner) {
        OwnerHomes state = new OwnerHomes();
        for (Home home : repository.findAll(owner)) {
            state.byName.put(home.name(), home);
        }
        return state;
    }

    // Lookup

    public Optional<Home> find(UUID owner, String name) {
        OwnerHomes state = resident.get(owner);
        return state == null ? Optional.empty() : Optional.ofNullable(state.byName.get(name));
    }

    public List<Home> findAll(UUID owner) {
        return sortedView(owner, HomeSort.ALPHABETICAL);
    }

    public int count(UUID owner) {
        OwnerHomes state = resident.get(owner);
        return state == null ? 0 : state.byName.size();
    }

    public PageResult<Home> query(HomeQuery query) {
        return PageResult.of(matching(query), query.page(), query.pageSize());
    }

    public HomeFacetCounts facets(HomeQuery query) {
        OwnerHomes state = resident.get(query.owner());
        if (state == null) {
            return HomeFacetCounts.of(Map.of(FilterConfig.ALL_ID, 0L), Map.of(FilterConfig.ALL_ID, 0L));
        }

        Map<String, Long> byWorld = new LinkedHashMap<>();
        Map<String, Long> byIcon = new LinkedHashMap<>();
        long worldTotal = 0;
        long iconTotal = 0;

        HomeQuery worldBase = query.withoutWorld();
        HomeQuery iconBase = query.withoutIcons();

        for (Home home : state.byName.values()) {
            if (matches(home, worldBase)) {
                byWorld.merge(home.position().world(), 1L, Long::sum);
                worldTotal++;
            }
            if (matches(home, iconBase)) {
                iconTotal++;
                if (home.icon() != null) {
                    byIcon.merge(home.icon(), 1L, Long::sum);
                }
            }
        }
        byWorld.put(FilterConfig.ALL_ID, worldTotal);
        byIcon.put(FilterConfig.ALL_ID, iconTotal);
        return HomeFacetCounts.of(byWorld, byIcon);
    }

    // Mutations

    public Home save(Home home) {
        OwnerHomes state = resident.get(home.owner());
        if (state == null) {
            writes.append(() -> repository.save(home));
            resident.load(home.owner());
            return home;
        }
        state.byName.put(home.name(), home);
        state.version.incrementAndGet();
        persist(home);
        return home;
    }

    public boolean delete(UUID owner, String name) {
        OwnerHomes state = resident.get(owner);
        if (state == null) {
            resident.load(owner);
            return false;
        }
        Home removed = state.byName.remove(name);
        if (removed == null) {
            return false;
        }
        state.version.incrementAndGet();
        writes.submit(new RowKey(owner, removed.name()), () -> repository.delete(owner, removed.name()));
        return true;
    }

    public int deleteAll(UUID owner) {
        OwnerHomes state = resident.get(owner);
        if (state == null) {
            resident.load(owner);
            return 0;
        }
        int deleted = state.byName.size();
        if (deleted == 0) {
            return 0;
        }
        state.byName.clear();
        state.version.incrementAndGet();
        writes.append(() -> repository.deleteAll(owner));
        return deleted;
    }

    public boolean setFavorite(UUID owner, String name, boolean favorite) {
        return persistIfPresent(mutate(owner, name, home -> home.withFavorite(favorite)));
    }

    public boolean setIcon(UUID owner, String name, @Nullable String icon) {
        return persistIfPresent(mutate(owner, name, home -> home.withIcon(icon)));
    }

    private @Nullable Home mutate(UUID owner, String name, UnaryOperator<Home> transform) {
        OwnerHomes state = resident.get(owner);
        if (state == null) {
            resident.load(owner);
            return null;
        }
        Home updated = state.byName.computeIfPresent(name, (key, current) -> transform.apply(current));
        if (updated != null) {
            state.version.incrementAndGet();
        }
        return updated;
    }

    private boolean persistIfPresent(@Nullable Home updated) {
        if (updated == null) {
            return false;
        }
        persist(updated);
        return true;
    }

    private void persist(Home home) {
        writes.submit(new RowKey(home.owner(), home.name()), () -> repository.save(home));
    }

    // Internal

    private List<Home> matching(HomeQuery query) {
        List<Home> result = new ArrayList<>();
        for (Home home : sortedView(query.owner(), query.sort())) {
            if (matches(home, query)) {
                result.add(home);
            }
        }
        return result;
    }

    private List<Home> sortedView(UUID owner, HomeSort sort) {
        OwnerHomes state = resident.get(owner);
        if (state == null) {
            return List.of();
        }
        return state.views.computeIfAbsent(sort, SortedView::new).get(state);
    }

    private boolean matches(Home home, HomeQuery query) {
        if (query.world() != null && !query.world().equals(home.position().world())) {
            return false;
        }
        if (query.icons() != null && (home.icon() == null || !query.icons().contains(home.icon()))) {
            return false;
        }
        if (query.favoritesOnly() && !home.favorite()) {
            return false;
        }
        String search = query.search();
        if (search == null || search.isBlank()) {
            return true;
        }
        return home.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    // Per-owner state

    private static final class OwnerHomes {
        final Map<String, Home> byName = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
        final AtomicLong version = new AtomicLong();
        final Map<HomeSort, SortedView> views = new ConcurrentHashMap<>();
    }

    // caches the full, unfiltered ordering for one (owner, sort) pair until that owner's version counter moves
    private static final class SortedView {
        private final HomeSort sort;
        private volatile Snapshot snapshot = Snapshot.EMPTY;

        SortedView(HomeSort sort) {
            this.sort = sort;
        }

        List<Home> get(OwnerHomes state) {
            long currentVersion = state.version.get();
            Snapshot current = snapshot;
            if (current.version() == currentVersion) {
                return current.homes();
            }
            synchronized (this) {
                current = snapshot;
                if (current.version() == currentVersion) {
                    return current.homes();
                }
                List<Home> rebuilt = new ArrayList<>(state.byName.values());
                rebuilt.sort(comparator(sort));
                Snapshot fresh = new Snapshot(currentVersion, List.copyOf(rebuilt));
                snapshot = fresh;
                return fresh.homes();
            }
        }
    }

    private record Snapshot(long version, List<Home> homes) {
        static final Snapshot EMPTY = new Snapshot(-1, List.of());
    }

    private static Comparator<Home> comparator(HomeSort sort) {
        Comparator<Home> nameOrder = Comparator.comparing(Home::name, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case NEWEST -> Comparator.comparing(Home::createdAt).reversed().thenComparing(nameOrder);
            case OLDEST -> Comparator.comparing(Home::createdAt).thenComparing(nameOrder);
            case ALPHABETICAL -> nameOrder;
            case FAVORITE -> Comparator.comparing(Home::favorite).reversed().thenComparing(nameOrder);
        };
    }
}