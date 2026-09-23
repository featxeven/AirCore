package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import org.bukkit.GameMode;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlayerRepository {

    record JoinResult(PlayerProfile profile, boolean firstJoin) {}

    record Identity(UUID uuid, String name, @Nullable String nickname, PlayerProfile.Skin skin) {}

    record BalanceEntry(Identity holder, double balance) {}

    Optional<PlayerProfile> find(UUID uuid);

    Optional<PlayerProfile> findByName(String name);

    Optional<UUID> findByNickname(String nickname);

    Map<UUID, PlayerProfile> findAll(Collection<UUID> uuids);

    // unknown uuids are simply absent from the map
    Map<UUID, Identity> findIdentities(Collection<UUID> uuids);

    Set<UUID> findAllUuids();

    JoinResult upsert(UUID uuid, String name, PlayerProfile.Skin skin, PlayerProfile.Toggles defaultToggles, double defaultBalance);

    void updateLastSeen(UUID uuid, Instant lastSeenAt);

    void updateLastLocation(UUID uuid, Position location);

    void updateNickname(UUID uuid, @Nullable String nickname, @Nullable String normalizedNickname);

    void updateChatChannel(UUID uuid, @Nullable String channel);

    void updateGameMode(UUID uuid, GameMode gameMode);

    void updateGodMode(UUID uuid, boolean enabled);

    void updateFlight(UUID uuid, PlayerProfile.Flight flight);

    void updateWalkSpeed(UUID uuid, float walkSpeed);

    void updateFlySpeed(UUID uuid, float flySpeed);

    void updatePlayerTime(UUID uuid, @Nullable Integer ticks);

    void updatePlayerWeather(UUID uuid, @Nullable PlayerProfile.PersonalWeather weather);

    void updateToggles(UUID uuid, PlayerProfile.Toggles toggles);

    void updateBalance(UUID uuid, double balance);

    void addBalance(UUID uuid, double amount);

    void addPendingPayment(UUID uuid, double amount);

    void clearPendingPayment(UUID uuid);

    double totalBalance();

    List<BalanceEntry> topBalances(boolean descending, int limit, double minValue);

    int deleteAll(Collection<UUID> uuids);

    int addBalanceToAll(double amount, double min, double max);

    int setBalanceForAll(double balance);

    int resetBalanceForAll(double defaultBalance);
}