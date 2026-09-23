package com.ftxeven.aircore.service;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.cache.WriteBehind;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.database.cache.PlayerCache;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

public final class PlayerService {
    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;
    private static final int BULK_REFRESH_CHUNK = 500;

    private final CacheManager cache;
    private final DatabaseManager database;
    private final ConfigManager configs;
    private final WriteBehind writes;

    public PlayerService(CacheManager cache, DatabaseManager database, ConfigManager configs) {
        this.cache = cache;
        this.database = database;
        this.configs = configs;
        this.writes = cache.writes();
    }

    // Lookups

    private enum Lookup { PEEK, FIND }

    private Optional<PlayerProfile> resolve(UUID uuid, Lookup lookup) {
        return lookup == Lookup.PEEK ? peek(uuid) : find(uuid);
    }

    public Optional<PlayerProfile> find(UUID uuid) {
        return cache.players().find(uuid);
    }

    public Optional<PlayerProfile> peek(UUID uuid) {
        return cache.players().peek(uuid);
    }

    public CompletableFuture<Void> ensureResident(UUID uuid) {
        if (cache.players().peek(uuid).isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Scheduler.runAsync(() -> {
            try {
                find(uuid); // admits into the cache as a side effect
                future.complete(null);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public Optional<PlayerProfile> findByRealName(String name) {
        return database.players().findByName(name).map(PlayerProfile::uuid).flatMap(this::find);
    }

    public Optional<PlayerProfile> findByNickname(String nickname) {
        return database.players().findByNickname(nickname).flatMap(this::find);
    }

    public Map<UUID, PlayerProfile> findAll(Collection<UUID> uuids) {
        return cache.players().findAll(uuids);
    }

    public Set<UUID> allUuids() {
        return database.players().findAllUuids();
    }

    public double totalBalance() {
        return cache.players().totalBalance();
    }

    public List<PlayerRepository.BalanceEntry> topBalances(boolean descending, int limit, double minValue) {
        List<PlayerRepository.BalanceEntry> ranked = database.players().topBalances(descending, limit, minValue);
        cache.players().admitIdentities(ranked.stream().map(PlayerRepository.BalanceEntry::holder).toList());
        return ranked;
    }

    public Optional<PlayerRepository.Identity> identity(UUID uuid) {
        return cache.players().identity(uuid);
    }

    public Optional<PlayerRepository.Identity> identityByName(String name) {
        return cache.players().identityByName(name);
    }

    public Map<UUID, PlayerRepository.Identity> identities(Collection<UUID> uuids) {
        return cache.players().identities(uuids);
    }

    public String name(UUID uuid) {
        return resolve(uuid, Lookup.FIND).map(PlayerProfile::name).orElse(uuid.toString());
    }

    // Join / quit handling

    public void handleJoin(Player player, BiConsumer<PlayerProfile, Boolean> onResolved) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        PlayerProfile.Skin skin = captureSkin(player);
        double defaultBalance = configs.economy().balance().defaultBalance();
        PlayerProfile.Toggles defaultToggles = defaultToggles();

        cache.cooldowns().warm(uuid, Instant.now());
        Scheduler.runAsync(() -> {
            PlayerRepository.JoinResult result = database.players()
                    .upsert(uuid, name, skin, defaultToggles, defaultBalance);
            cache.players().warm(result.profile());
            Scheduler.runEntity(player, () -> {
                applyState(player, result.profile());
                onResolved.accept(result.profile(), result.firstJoin());
            });
        });
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Position lastLocation = Positions.of(player.getLocation());
        updateLastSeen(uuid, Instant.now());
        updateLastLocation(uuid, lastLocation);
        cache.players().invalidate(uuid);
        cache.cooldowns().invalidate(uuid);
    }

    private PlayerProfile.Skin captureSkin(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if ("textures".equals(property.getName())
                    && property.getSignature() != null
                    && !property.getSignature().isEmpty()) {
                return new PlayerProfile.Skin(property.getValue(), property.getSignature());
            }
        }
        return PlayerProfile.Skin.EMPTY;
    }

    private PlayerProfile.Toggles defaultToggles() {
        MainConfig.ToggleDefaults defaults = configs.main().toggleDefaults();
        return new PlayerProfile.Toggles(
                defaults.msgToggle(), defaults.socialSpy(), defaults.chatToggle(), defaults.mentionToggle(),
                defaults.announceToggle(), defaults.payToggle(), defaults.payConfirmToggle(), defaults.tpToggle(),
                defaults.tpAutoAccept(), defaults.tpConfirmToggle()
        );
    }

    private void applyState(Player player, PlayerProfile profile) {
        player.setGameMode(profile.gameMode());
        applyRestrictions(player, profile);
    }

    public void reapplyWorldRestrictions(Player player) {
        peek(player.getUniqueId()).ifPresent(profile -> applyRestrictions(player, profile));
    }

    private void applyRestrictions(Player player, PlayerProfile profile) {
        boolean godRestricted = isFeatureRestricted(player, MainConfig.RestrictedFeature.GOD);
        player.setInvulnerable(profile.godMode() && !godRestricted);
        applyFlightState(player, profile);

        boolean speedRestricted = isFeatureRestricted(player, MainConfig.RestrictedFeature.SPEED);
        player.setWalkSpeed(speedRestricted ? DEFAULT_WALK_SPEED : profile.walkSpeed());
        player.setFlySpeed(speedRestricted ? DEFAULT_FLY_SPEED : profile.flySpeed());

        boolean timeRestricted = isFeatureRestricted(player, MainConfig.RestrictedFeature.CUSTOM_TIME);
        if (profile.playerTime() != null && !timeRestricted) {
            player.setPlayerTime(profile.playerTime(), false);
        } else {
            player.resetPlayerTime();
        }

        boolean weatherRestricted = isFeatureRestricted(player, MainConfig.RestrictedFeature.CUSTOM_WEATHER);
        if (profile.playerWeather() != null && !weatherRestricted) {
            player.setPlayerWeather(profile.playerWeather().toBukkit());
        } else {
            player.resetPlayerWeather();
        }
    }

    public void reapplyFlightState(Player player) {
        peek(player.getUniqueId()).ifPresent(profile -> applyFlightState(player, profile));
    }

    private void applyFlightState(Player player, PlayerProfile profile) {
        boolean pluginFlightAllowed = profile.flight().allowed()
                && !isFeatureRestricted(player, MainConfig.RestrictedFeature.FLY);
        boolean allowFlight = pluginFlightAllowed || impliesFlight(profile.gameMode());
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && profile.flight().flying());
    }

    private boolean impliesFlight(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    // World restrictions

    public boolean isFeatureRestricted(Player player, MainConfig.RestrictedFeature feature) {
        if (player.hasPermission(bypassPermission(feature))) {
            return false;
        }
        return isFeatureRestricted(player.getWorld().getName(), feature);
    }

    public boolean isFeatureRestricted(String world, MainConfig.RestrictedFeature feature) {
        return configs.main().worldRestrictions().isRestricted(world, feature);
    }

    public String bypassPermission(MainConfig.RestrictedFeature feature) {
        String key = feature.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return Permissions.Bypass.restriction(key);
    }

    // Profile updates

    /** Absolute state for one field: safe to coalesce, so high-frequency writes collapse */
    private void mutate(UUID uuid, UnaryOperator<PlayerProfile> transform, String field, WriteBehind.Write persist) {
        cache.players().update(uuid, transform);
        writes.submit(new WriteBehind.Field(uuid, field), persist);
    }

    /** deltas and anything order-sensitive */
    private void accumulate(UUID uuid, UnaryOperator<PlayerProfile> transform, WriteBehind.Write persist) {
        cache.players().update(uuid, transform);
        writes.append(persist);
    }

    public <R> Optional<R> computeProfile(UUID uuid, Function<PlayerProfile, PlayerCache.Change<R>> fn) {
        Optional<R> result = cache.players().compute(uuid, fn);
        if (result.isEmpty()) {
            ensureResident(uuid);
        }
        return result;
    }

    public void updateLastSeen(UUID uuid, Instant lastSeenAt) {
        writes.submit(
                new WriteBehind.Field(uuid, "last-seen"),
                () -> database.players().updateLastSeen(uuid, lastSeenAt)
        );
    }

    public void updateLastLocation(UUID uuid, Position location) {
        mutate(uuid, p -> p.withLastLocation(location), "last-location",
                () -> database.players().updateLastLocation(uuid, location));
    }

    public void updateNickname(UUID uuid, @Nullable String nickname) {
        String normalized = normalizedNickname(nickname);
        mutate(uuid, p -> p.withNickname(nickname), "nickname",
                () -> database.players().updateNickname(uuid, nickname, normalized));
    }

    private @Nullable String normalizedNickname(@Nullable String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return null;
        }
        try {
            return MiniText.plain(nickname).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return nickname.toLowerCase(Locale.ROOT);
        }
    }

    public void updateChatChannel(UUID uuid, @Nullable String channel) {
        mutate(uuid, p -> p.withChatChannel(channel), "chat-channel",
                () -> database.players().updateChatChannel(uuid, channel));
    }

    public void updateGameMode(UUID uuid, GameMode gameMode) {
        Optional<PlayerProfile> current = peek(uuid);
        if (current.map(PlayerProfile::gameMode).filter(existing -> existing == gameMode).isPresent()) {
            return;
        }
        mutate(uuid, p -> p.withGameMode(gameMode), "game-mode",
                () -> database.players().updateGameMode(uuid, gameMode));
    }

    public void updateGodMode(UUID uuid, boolean enabled) {
        mutate(uuid, p -> p.withGodMode(enabled), "god-mode",
                () -> database.players().updateGodMode(uuid, enabled));
    }

    public void updateFlight(UUID uuid, PlayerProfile.Flight flight) {
        mutate(uuid, p -> p.withFlight(flight), "flight",
                () -> database.players().updateFlight(uuid, flight));
    }

    public void updateFlying(UUID uuid, boolean flying) {
        PlayerProfile.Flight current = peek(uuid)
                .map(PlayerProfile::flight)
                .orElse(PlayerProfile.Flight.GROUNDED);
        if (current.flying() == flying) {
            return;
        }
        updateFlight(uuid, new PlayerProfile.Flight(current.allowed(), flying));
    }

    public void updateWalkSpeed(UUID uuid, float walkSpeed) {
        mutate(uuid, p -> p.withWalkSpeed(walkSpeed), "walk-speed",
                () -> database.players().updateWalkSpeed(uuid, walkSpeed));
    }

    public void updateFlySpeed(UUID uuid, float flySpeed) {
        mutate(uuid, p -> p.withFlySpeed(flySpeed), "fly-speed",
                () -> database.players().updateFlySpeed(uuid, flySpeed));
    }

    public void updatePlayerTime(UUID uuid, @Nullable Integer ticks) {
        mutate(uuid, p -> p.withPlayerTime(ticks), "player-time",
                () -> database.players().updatePlayerTime(uuid, ticks));
    }

    public void updatePlayerWeather(UUID uuid, @Nullable PlayerProfile.PersonalWeather weather) {
        mutate(uuid, p -> p.withPlayerWeather(weather), "player-weather",
                () -> database.players().updatePlayerWeather(uuid, weather));
    }

    public void updateToggles(UUID uuid, PlayerProfile.Toggles toggles) {
        mutate(uuid, p -> p.withToggles(toggles), "toggles",
                () -> database.players().updateToggles(uuid, toggles));
    }

    public void updateBalance(UUID uuid, double balance) {
        accumulate(uuid, p -> p.withBalance(balance),
                () -> database.players().updateBalance(uuid, balance));
    }

    public void addBalance(UUID uuid, double amount) {
        accumulate(uuid, p -> p.withBalance(p.balance() + amount),
                () -> database.players().addBalance(uuid, amount));
    }

    public void addPendingPayment(UUID uuid, double amount) {
        accumulate(uuid, p -> p.withPendingPayment(p.pendingPayment() + amount),
                () -> database.players().addPendingPayment(uuid, amount));
    }

    public void clearPendingPayment(UUID uuid) {
        accumulate(uuid, p -> p.withPendingPayment(0.0),
                () -> database.players().clearPendingPayment(uuid));
    }

    public void persistBalanceDelta(UUID uuid, double delta) {
        writes.append(() -> database.players().addBalance(uuid, delta));
    }

    public void persistBalance(UUID uuid, double balance) {
        writes.append(() -> database.players().updateBalance(uuid, balance));
    }

    public int deleteAll(Collection<UUID> uuids) {
        int deleted = database.players().deleteAll(uuids);
        uuids.forEach(cache.players()::invalidate);
        return deleted;
    }

    // Bulk balance operations

    public int addBalanceToAllPersisted(double amount, double min, double max) {
        return applyBulk(() -> database.players().addBalanceToAll(amount, min, max));
    }

    public int setBalanceForAllPersisted(double balance) {
        return applyBulk(() -> database.players().setBalanceForAll(balance));
    }

    public int resetBalanceForAllPersisted(double defaultBalance) {
        return applyBulk(() -> database.players().resetBalanceForAll(defaultBalance));
    }

    private int applyBulk(IntSupplier statement) {
        writes.flush();
        int affected = statement.getAsInt();
        refreshAfterBulk();
        return affected;
    }

    private void refreshAfterBulk() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }

        for (UUID resident : cache.players().residentIds()) {
            if (!online.contains(resident)) {
                cache.players().invalidate(resident);
            }
        }

        List<UUID> ids = List.copyOf(online);
        for (int from = 0; from < ids.size(); from += BULK_REFRESH_CHUNK) {
            List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + BULK_REFRESH_CHUNK));
            database.players().findAll(chunk).forEach((uuid, fresh) ->
                    cache.players().update(uuid, profile -> profile.withBalance(fresh.balance())));
        }
        cache.players().invalidateTotalBalance();
    }

    // Display name placeholders

    // Writes %player% (+ %player_realname%) for a resolved profile
    public void formatDisplayName(Map<String, String> placeholders, PlayerProfile profile) {
        formatDisplayName(placeholders, "player", profile);
    }

    // same, into a custom key e.g. %target%/%target_realname%, %sender%/%sender_realname%
    public void formatDisplayName(Map<String, String> placeholders, String key, PlayerProfile profile) {
        put(placeholders, key, displayName(profile), profile.name());
    }

    // same, resolved by uuid (cache, falling back to db). Falls back to the raw uuid if the player has no profile
    public void formatDisplayName(Map<String, String> placeholders, String key, UUID uuid) {
        Optional<PlayerProfile> profile = resolve(uuid, Lookup.FIND);
        put(placeholders, key,
                profile.map(this::displayName).orElse(uuid.toString()),
                profile.map(PlayerProfile::name).orElse(uuid.toString()));
    }

    // same, resolved from a live Player (cache-only, no db fallback).
    // falls back to the live bukkit name if no profile is cached yet
    public void formatDisplayName(Map<String, String> placeholders, String key, Player player) {
        Optional<PlayerProfile> profile = resolve(player.getUniqueId(), Lookup.PEEK);
        put(placeholders, key,
                profile.map(this::displayName).orElseGet(player::getName),
                profile.map(PlayerProfile::name).orElseGet(player::getName));
    }

    // same, from an already-loaded identity: no cache or db access
    public void formatDisplayName(Map<String, String> placeholders, String key, PlayerRepository.Identity identity) {
        put(placeholders, key, display(identity.name(), identity.nickname()), identity.name());
    }

    // %player% for whoever ran a command: their display name, or the configured console name
    public void formatSender(Map<String, String> placeholders, String key, CommandSender sender) {
        if (sender instanceof Player player) {
            formatDisplayName(placeholders, key, player);
            return;
        }
        String consoleName = configs.lang().get("general.console-name").getFirst();
        put(placeholders, key, consoleName, consoleName);
    }

    private void put(Map<String, String> placeholders, String key, String display, String realName) {
        placeholders.put(key, display);
        placeholders.put(key + "_realname", realName);
    }

    public String displayName(PlayerProfile profile) {
        return display(profile.name(), profile.nickname());
    }

    private String display(String name, @Nullable String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return name;
        }
        return configs.extras().nicknames().prefix() + nickname;
    }
}