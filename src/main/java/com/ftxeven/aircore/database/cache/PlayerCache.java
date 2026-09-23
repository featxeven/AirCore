package com.ftxeven.aircore.database.cache;

import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.model.PlayerProfile;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class PlayerCache {

    private static final String TOTAL_BALANCE_KEY = "TOTAL_BALANCE";

    private record Entry(PlayerProfile profile, long expiresAtNanos) {
        boolean expired(long nowNanos) {
            return nowNanos - expiresAtNanos >= 0; // overflow-safe nanoTime comparison
        }
    }

    private final PlayerRepository repository;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> identityNames = new ConcurrentHashMap<>();
    private final TtlCache<UUID, PlayerRepository.Identity> identities;
    private final TtlCache<String, Double> totalBalance;
    private final long offlineTtlNanos;
    private final Predicate<UUID> isOnline;

    public PlayerCache(PlayerRepository repository, Duration totalBalanceTtl, Duration identityTtl,
                       Duration offlineTtl, Predicate<UUID> isOnline) {
        this.repository = repository;
        this.totalBalance = new TtlCache<>(totalBalanceTtl);
        this.identities = new TtlCache<>(identityTtl);
        this.offlineTtlNanos = offlineTtl.toNanos();
        this.isOnline = isOnline;
    }

    // Full-profile lookups

    public Optional<PlayerProfile> find(UUID uuid) {
        PlayerProfile resident = resident(uuid);
        if (resident != null) {
            return Optional.of(resident);
        }
        return repository.find(uuid).map(this::admit);
    }

    public Optional<PlayerProfile> peek(UUID uuid) {
        return Optional.ofNullable(resident(uuid));
    }

    public Map<UUID, PlayerProfile> findAll(Collection<UUID> uuids) {
        Map<UUID, PlayerProfile> result = new LinkedHashMap<>(uuids.size());
        List<UUID> missing = new ArrayList<>();

        for (UUID uuid : uuids) {
            PlayerProfile resident = resident(uuid);
            if (resident != null) {
                result.put(uuid, resident);
            } else {
                missing.add(uuid);
            }
        }

        if (!missing.isEmpty()) {
            repository.findAll(missing).forEach((id, profile) -> result.put(id, admit(profile)));
        }
        return result;
    }

    // Identity lookups

    public Optional<PlayerRepository.Identity> identity(UUID uuid) {
        PlayerProfile resident = resident(uuid);
        if (resident != null) {
            return Optional.of(identityOf(resident));
        }
        Optional<PlayerRepository.Identity> cached = identities.get(uuid);
        if (cached.isPresent()) {
            return cached;
        }
        return repository.findIdentities(List.of(uuid)).values().stream().findFirst().map(this::admitIdentity);
    }

    public Optional<PlayerRepository.Identity> identityByName(String name) {
        UUID known = identityNames.get(name.toLowerCase(Locale.ROOT));
        if (known != null) {
            return identity(known);
        }
        return repository.findByName(name).map(profile -> admitIdentity(identityOf(profile)));
    }

    public Map<UUID, PlayerRepository.Identity> identities(Collection<UUID> uuids) {
        Map<UUID, PlayerRepository.Identity> result = new LinkedHashMap<>(uuids.size());
        List<UUID> missing = new ArrayList<>();

        for (UUID uuid : uuids) {
            PlayerProfile resident = resident(uuid);
            if (resident != null) {
                result.put(uuid, identityOf(resident));
                continue;
            }
            Optional<PlayerRepository.Identity> cached = identities.get(uuid);
            if (cached.isPresent()) {
                result.put(uuid, cached.get());
            } else {
                missing.add(uuid);
            }
        }

        if (!missing.isEmpty()) {
            repository.findIdentities(missing).forEach((uuid, loaded) -> result.put(uuid, admitIdentity(loaded)));
        }
        return result;
    }

    /** Folds already-known identities into the cache for free */
    public void admitIdentities(Collection<PlayerRepository.Identity> loaded) {
        loaded.forEach(this::admitIdentity);
    }

    // Writes

    public void warm(PlayerProfile profile) {
        cache.put(profile.uuid(), new Entry(profile, deadline()));
        identityNames.put(profile.name().toLowerCase(Locale.ROOT), profile.uuid());
    }

    public void update(UUID uuid, UnaryOperator<PlayerProfile> transform) {
        cache.computeIfPresent(uuid, (id, entry) -> new Entry(transform.apply(entry.profile()), deadline()));
    }

    public record Change<R>(@Nullable PlayerProfile updated, @Nullable R result) {

        public static <R> Change<R> of(PlayerProfile updated, R result) {
            return new Change<>(updated, result);
        }

        public static <R> Change<R> unchanged(R result) {
            return new Change<>(null, result);
        }
    }

    public <R> Optional<R> compute(UUID uuid, Function<PlayerProfile, Change<R>> fn) {
        AtomicReference<Change<R>> outcome = new AtomicReference<>();

        cache.computeIfPresent(uuid, (id, entry) -> {
            Change<R> change = fn.apply(entry.profile());
            outcome.set(change);
            return change.updated() != null ? new Entry(change.updated(), deadline()) : entry;
        });

        Change<R> change = outcome.get();
        return change == null
                ? Optional.empty()
                : Optional.ofNullable(change.result());
    }

    public double totalBalance() {
        return totalBalance.resolve(TOTAL_BALANCE_KEY, repository::totalBalance);
    }

    // Residency

    public Set<UUID> residentIds() {
        return Set.copyOf(cache.keySet());
    }

    public int evictExpired() {
        int evicted = 0;
        long now = System.nanoTime();
        for (Map.Entry<UUID, Entry> slot : cache.entrySet()) {
            Entry entry = slot.getValue();
            if (!entry.expired(now)) {
                continue;
            }
            UUID uuid = slot.getKey();
            if (isOnline.test(uuid)) {
                cache.replace(uuid, entry, new Entry(entry.profile(), now + offlineTtlNanos));
            } else if (cache.remove(uuid, entry)) {
                evicted++;
            }
        }
        return evicted;
    }

    public void invalidateTotalBalance() {
        totalBalance.clear();
    }

    public void invalidate(UUID uuid) {
        Entry removed = cache.remove(uuid);
        if (removed != null) {
            identityNames.remove(removed.profile().name().toLowerCase(Locale.ROOT), uuid);
        }
        identities.remove(uuid);
    }

    public void invalidateAll() {
        cache.clear();
        identities.clear();
        identityNames.clear();
        totalBalance.clear();
    }

    // Internal

    private @Nullable PlayerProfile resident(UUID uuid) {
        Entry entry = cache.get(uuid);
        if (entry == null) {
            return null;
        }

        long now = System.nanoTime();
        if (!entry.expired(now)) {
            return entry.profile();
        }

        if (isOnline.test(uuid)) {
            if (cache.replace(uuid, entry, new Entry(entry.profile(), now + offlineTtlNanos))) {
                return entry.profile();
            }
        } else if (cache.remove(uuid, entry)) {
            return null;
        }
        Entry latest = cache.get(uuid);
        return latest != null ? latest.profile() : null;
    }

    private PlayerProfile admit(PlayerProfile loaded) {
        Entry existing = cache.putIfAbsent(loaded.uuid(), new Entry(loaded, deadline()));
        PlayerProfile resident = existing != null ? existing.profile() : loaded;
        identityNames.put(resident.name().toLowerCase(Locale.ROOT), resident.uuid());
        return resident;
    }

    private PlayerRepository.Identity admitIdentity(PlayerRepository.Identity loaded) {
        identities.put(loaded.uuid(), loaded);
        identityNames.put(loaded.name().toLowerCase(Locale.ROOT), loaded.uuid());
        return loaded;
    }

    private static PlayerRepository.Identity identityOf(PlayerProfile profile) {
        return new PlayerRepository.Identity(profile.uuid(), profile.name(), profile.nickname(), profile.skin());
    }

    private long deadline() {
        return System.nanoTime() + offlineTtlNanos;
    }
}