package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.CooldownScope;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CooldownRepository {

    // Sentinel expiry for a cooldown that never lapses
    Instant PERMANENT = Instant.ofEpochMilli(Long.MAX_VALUE);

    Optional<Instant> find(UUID owner, CooldownScope scope, String key, String arg, Instant now);

    // every still-active cooldown a player has, across all scopes
    List<Cooldown> findAllActive(UUID owner, Instant now);

    void set(UUID owner, CooldownScope scope, String key, String arg, Instant expiresAt);

    void clear(UUID owner, CooldownScope scope, String key, String arg);

    void clearAll(UUID owner, CooldownScope scope);

    // drops every player's cooldown row for one key within a scope - used when the underlying
    // object is deleted (e.g. a kit), so orphaned rows (including permanent claims) don't linger
    int clearAllForKey(CooldownScope scope, String key);

    // drops rows that expired before the cutoff. PERMANENT rows never match "expires_at <=
    // before", so one-time claims are naturally immune to purging
    int purgeExpired(Instant before);
}