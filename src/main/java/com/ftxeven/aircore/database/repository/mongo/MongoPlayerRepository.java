package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.id.MongoSequence;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.GameMode;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;

public final class MongoPlayerRepository implements PlayerRepository {

    private static final Collation CASE_INSENSITIVE = Collation.builder()
            .locale("en")
            .collationStrength(CollationStrength.SECONDARY)
            .build();

    private final MongoCollection<Document> players;
    private final MongoCollection<Document> counters;

    public MongoPlayerRepository(MongoDatabase database, String tablePrefix) {
        this.players = database.getCollection(tablePrefix + "players");
        this.counters = database.getCollection(tablePrefix + "counters");
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        Document doc = players.find(eq("_id", uuid.toString())).first();
        return doc != null ? Optional.of(mapProfile(doc)) : Optional.empty();
    }

    @Override
    public Optional<PlayerProfile> findByName(String name) {
        Document doc = players.find(eq("name", name)).collation(CASE_INSENSITIVE).first();
        return doc != null ? Optional.of(mapProfile(doc)) : Optional.empty();
    }

    @Override
    public Optional<UUID> findByNickname(String nickname) {
        Document doc = players.find(eq("nicknameNormalized", nickname.toLowerCase(Locale.ROOT)))
                .projection(Projections.include("_id")).first();
        return doc != null ? Optional.of(UUID.fromString(doc.getString("_id"))) : Optional.empty();
    }

    @Override
    public Map<UUID, PlayerProfile> findAll(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        List<String> ids = uuids.stream().map(UUID::toString).toList();
        Map<UUID, PlayerProfile> result = new LinkedHashMap<>();
        for (Document doc : players.find(Filters.in("_id", ids))) {
            PlayerProfile profile = mapProfile(doc);
            result.put(profile.uuid(), profile);
        }
        return result;
    }

    @Override
    public Map<UUID, Identity> findIdentities(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        List<String> ids = uuids.stream().map(UUID::toString).toList();
        Map<UUID, Identity> result = HashMap.newHashMap(ids.size());
        for (Document doc : players.find(Filters.in("_id", ids))
                .projection(Projections.include("name", "nickname", "skinValue", "skinSignature"))) {
            Identity identity = mapIdentity(doc);
            result.put(identity.uuid(), identity);
        }
        return result;
    }

    @Override
    public Set<UUID> findAllUuids() {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (Document doc : players.find().projection(Projections.include("_id"))) {
            uuids.add(UUID.fromString(doc.getString("_id")));
        }
        return uuids;
    }

    @Override
    public JoinResult upsert(UUID uuid, String name, PlayerProfile.Skin skin, PlayerProfile.Toggles defaultToggles, double defaultBalance) {
        Document existing = players.find(eq("_id", uuid.toString())).first();
        if (existing != null) {
            players.updateOne(eq("_id", uuid.toString()), Updates.combine(
                    Updates.set("name", name),
                    Updates.set("skinValue", skin.value()),
                    Updates.set("skinSignature", skin.signature())));
            existing.put("name", name);
            existing.put("skinValue", skin.value());
            existing.put("skinSignature", skin.signature());
            return new JoinResult(mapProfile(existing), false);
        }

        int joinNumber = MongoSequence.next(counters, "player_join_number");
        Instant now = Instant.now();

        Document doc = new Document("_id", uuid.toString())
                .append("name", name)
                .append("nickname", null)
                .append("nicknameNormalized", null)
                .append("skinValue", skin.value())
                .append("skinSignature", skin.signature())
                .append("joinNumber", joinNumber)
                .append("firstJoinAt", Date.from(now))
                .append("lastSeenAt", Date.from(now))
                .append("lastLocation", null)
                .append("chatChannel", null)
                .append("gameMode", GameMode.SURVIVAL.name())
                .append("godMode", false)
                .append("allowFlight", false)
                .append("flying", false)
                .append("walkSpeed", 0.2)
                .append("flySpeed", 0.1)
                .append("playerTime", null)
                .append("playerWeather", null)
                .append("toggles", defaultToggles.toBits())
                .append("pendingPayment", 0.0)
                .append("balance", defaultBalance);

        players.insertOne(doc);
        return new JoinResult(mapProfile(doc), true);
    }

    @Override
    public void updateLastSeen(UUID uuid, Instant lastSeenAt) {
        set(uuid, "lastSeenAt", Date.from(lastSeenAt));
    }

    @Override
    public void updateLastLocation(UUID uuid, Position location) {
        Document loc = new Document("world", location.world())
                .append("x", location.x()).append("y", location.y()).append("z", location.z())
                .append("yaw", (double) location.yaw()).append("pitch", (double) location.pitch());
        set(uuid, "lastLocation", loc);
    }

    @Override
    public void updateNickname(UUID uuid, @Nullable String nickname, @Nullable String normalizedNickname) {
        players.updateOne(eq("_id", uuid.toString()), Updates.combine(
                Updates.set("nickname", nickname),
                Updates.set("nicknameNormalized", normalizedNickname)));
    }

    @Override
    public void updateChatChannel(UUID uuid, @Nullable String channel) {
        set(uuid, "chatChannel", channel);
    }

    @Override
    public void updateGameMode(UUID uuid, GameMode gameMode) {
        set(uuid, "gameMode", gameMode.name());
    }

    @Override
    public void updateGodMode(UUID uuid, boolean enabled) {
        set(uuid, "godMode", enabled);
    }

    @Override
    public void updateFlight(UUID uuid, PlayerProfile.Flight flight) {
        players.updateOne(eq("_id", uuid.toString()), Updates.combine(
                Updates.set("allowFlight", flight.allowed()),
                Updates.set("flying", flight.flying())));
    }

    @Override
    public void updateWalkSpeed(UUID uuid, float walkSpeed) {
        set(uuid, "walkSpeed", (double) walkSpeed);
    }

    @Override
    public void updateFlySpeed(UUID uuid, float flySpeed) {
        set(uuid, "flySpeed", (double) flySpeed);
    }

    @Override
    public void updatePlayerTime(UUID uuid, @Nullable Integer ticks) {
        set(uuid, "playerTime", ticks);
    }

    @Override
    public void updatePlayerWeather(UUID uuid, @Nullable PlayerProfile.PersonalWeather weather) {
        set(uuid, "playerWeather", weather != null ? weather.name() : null);
    }

    @Override
    public void updateToggles(UUID uuid, PlayerProfile.Toggles toggles) {
        set(uuid, "toggles", toggles.toBits());
    }

    @Override
    public void updateBalance(UUID uuid, double balance) {
        set(uuid, "balance", balance);
    }

    @Override
    public void addBalance(UUID uuid, double amount) {
        players.updateOne(eq("_id", uuid.toString()), Updates.inc("balance", amount));
    }

    @Override
    public void addPendingPayment(UUID uuid, double amount) {
        players.updateOne(eq("_id", uuid.toString()), Updates.inc("pendingPayment", amount));
    }

    @Override
    public void clearPendingPayment(UUID uuid) {
        set(uuid, "pendingPayment", 0.0);
    }

    @Override
    public double totalBalance() {
        Document result = players.aggregate(List.of(
                Aggregates.group(null, Accumulators.sum("total", "$balance"))
        )).first();
        return result != null ? result.get("total", Number.class).doubleValue() : 0.0;
    }

    @Override
    public List<BalanceEntry> topBalances(boolean descending, int limit, double minValue) {
        Bson sort = descending ? Sorts.descending("balance", "_id") : Sorts.ascending("balance", "_id");
        FindIterable<Document> find = players.find(Filters.gte("balance", minValue))
                .projection(Projections.include("name", "nickname", "balance", "skinValue", "skinSignature"))
                .sort(sort);
        if (limit > 0) {
            find = find.limit(limit);
        }
        List<BalanceEntry> ranked = new ArrayList<>();
        for (Document doc : find) {
            Number balance = doc.get("balance", Number.class);
            ranked.add(new BalanceEntry(mapIdentity(doc), balance != null ? balance.doubleValue() : 0.0));
        }
        return ranked;
    }

    @Override
    public int deleteAll(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return 0;
        }
        List<String> ids = uuids.stream().map(UUID::toString).toList();
        return (int) players.deleteMany(Filters.in("_id", ids)).getDeletedCount();
    }

    // Bulk balance operations

    @Override
    public int addBalanceToAll(double amount, double min, double max) {
        Document current = new Document("$ifNull", Arrays.asList("$balance", 0));
        Document sum = new Document("$add", Arrays.asList(current, amount));

        if (amount >= 0 && max != -1) {
            return setBalanceFromExpression(clamp("$gte", current, "$min", sum, max));
        }
        if (amount < 0 && min != -1) {
            return setBalanceFromExpression(clamp("$lte", current, "$max", sum, min));
        }
        return (int) players.updateMany(new Document(), Updates.inc("balance", amount)).getMatchedCount();
    }

    private static Document clamp(String pastBound, Document current, String limiter, Document sum, double bound) {
        return new Document("$cond", Arrays.asList(
                new Document(pastBound, Arrays.asList(current, bound)),
                current,
                new Document(limiter, Arrays.asList(sum, bound))));
    }

    private int setBalanceFromExpression(Document expression) {
        return (int) players.updateMany(new Document(),
                List.of(new Document("$set", new Document("balance", expression)))).getMatchedCount();
    }

    @Override
    public int setBalanceForAll(double balance) {
        return (int) players.updateMany(new Document(), Updates.set("balance", balance)).getMatchedCount();
    }

    @Override
    public int resetBalanceForAll(double defaultBalance) {
        return setBalanceForAll(defaultBalance);
    }

    private void set(UUID uuid, String field, Object value) {
        players.updateOne(eq("_id", uuid.toString()), Updates.set(field, value));
    }

    // Row mapping

    private Identity mapIdentity(Document doc) {
        return new Identity(
                UUID.fromString(doc.getString("_id")),
                doc.getString("name"),
                doc.getString("nickname"),
                new PlayerProfile.Skin(doc.getString("skinValue"), doc.getString("skinSignature")));
    }

    private PlayerProfile mapProfile(Document doc) {
        Document loc = doc.get("lastLocation", Document.class);
        String weatherRaw = doc.getString("playerWeather");
        Number pendingPayment = doc.get("pendingPayment", Number.class);
        Number balance = doc.get("balance", Number.class);

        return new PlayerProfile(
                UUID.fromString(doc.getString("_id")),
                doc.getString("name"),
                doc.getString("nickname"),
                new PlayerProfile.Skin(doc.getString("skinValue"), doc.getString("skinSignature")),
                doc.getInteger("joinNumber"),
                doc.getDate("firstJoinAt").toInstant(),
                doc.getDate("lastSeenAt").toInstant(),
                loc != null ? new Position(loc.getString("world"),
                        loc.get("x", Number.class).doubleValue(), loc.get("y", Number.class).doubleValue(), loc.get("z", Number.class).doubleValue(),
                        loc.get("yaw", Number.class).floatValue(), loc.get("pitch", Number.class).floatValue()) : null,
                doc.getString("chatChannel"),
                GameMode.valueOf(doc.getString("gameMode")),
                doc.getBoolean("godMode"),
                new PlayerProfile.Flight(doc.getBoolean("allowFlight"), doc.getBoolean("flying")),
                doc.get("walkSpeed", Number.class).floatValue(),
                doc.get("flySpeed", Number.class).floatValue(),
                doc.getInteger("playerTime"),
                weatherRaw != null ? PlayerProfile.PersonalWeather.valueOf(weatherRaw) : null,
                PlayerProfile.Toggles.fromBits(doc.getInteger("toggles")),
                pendingPayment != null ? pendingPayment.doubleValue() : 0.0,
                balance != null ? balance.doubleValue() : 0.0
        );
    }
}