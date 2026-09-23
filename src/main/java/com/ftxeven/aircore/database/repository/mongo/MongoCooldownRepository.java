package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.CooldownScope;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;

public final class MongoCooldownRepository implements CooldownRepository {

    private final MongoCollection<Document> cooldowns;

    public MongoCooldownRepository(MongoDatabase database, String tablePrefix) {
        this.cooldowns = database.getCollection(tablePrefix + "cooldowns");
    }

    @Override
    public Optional<Instant> find(UUID owner, CooldownScope scope, String key, String arg, Instant now) {
        Document doc = cooldowns.find(and(filter(owner, scope, key, arg), gt("expiresAt", Date.from(now)))).first();
        return doc != null ? Optional.of(doc.getDate("expiresAt").toInstant()) : Optional.empty();
    }

    @Override
    public List<Cooldown> findAllActive(UUID owner, Instant now) {
        List<Cooldown> active = new ArrayList<>();
        for (Document doc : cooldowns.find(and(eq("owner", owner.toString()), gt("expiresAt", Date.from(now))))) {
            active.add(new Cooldown(owner, CooldownScope.valueOf(doc.getString("scope")),
                    doc.getString("key"), doc.getString("arg"), doc.getDate("expiresAt").toInstant()));
        }
        return active;
    }

    @Override
    public void set(UUID owner, CooldownScope scope, String key, String arg, Instant expiresAt) {
        cooldowns.updateOne(filter(owner, scope, key, arg), Updates.set("expiresAt", Date.from(expiresAt)), new UpdateOptions().upsert(true));
    }

    @Override
    public void clear(UUID owner, CooldownScope scope, String key, String arg) {
        cooldowns.deleteOne(filter(owner, scope, key, arg));
    }

    @Override
    public void clearAll(UUID owner, CooldownScope scope) {
        cooldowns.deleteMany(and(eq("owner", owner.toString()), eq("scope", scope.name())));
    }

    @Override
    public int clearAllForKey(CooldownScope scope, String key) {
        return (int) cooldowns.deleteMany(and(eq("scope", scope.name()), eq("key", key))).getDeletedCount();
    }

    @Override
    public int purgeExpired(Instant before) {
        return (int) cooldowns.deleteMany(lte("expiresAt", Date.from(before))).getDeletedCount();
    }

    private Bson filter(UUID owner, CooldownScope scope, String key, String arg) {
        return and(eq("owner", owner.toString()), eq("scope", scope.name()), eq("key", key), eq("arg", arg));
    }
}