package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.BlockRepository;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public final class MongoBlockRepository implements BlockRepository {

    private final MongoCollection<Document> blocks;

    public MongoBlockRepository(MongoDatabase database, String tablePrefix) {
        this.blocks = database.getCollection(tablePrefix + "blocks");
    }

    @Override
    public Set<UUID> blockedBy(UUID owner) {
        Set<UUID> blocked = new LinkedHashSet<>();
        for (Document doc : blocks.find(eq("owner", owner.toString()))) {
            blocked.add(UUID.fromString(doc.getString("blocked")));
        }
        return blocked;
    }

    @Override
    public boolean isBlocked(UUID owner, UUID target) {
        return blocks.find(and(eq("owner", owner.toString()), eq("blocked", target.toString()))).first() != null;
    }

    @Override
    public int countBlocked(UUID owner) {
        return (int) blocks.countDocuments(eq("owner", owner.toString()));
    }

    @Override
    public boolean block(UUID owner, UUID target) {
        if (isBlocked(owner, target)) {
            return false;
        }
        Document doc = new Document("owner", owner.toString())
                .append("blocked", target.toString())
                .append("blockedAt", Date.from(Instant.now()));
        try {
            blocks.insertOne(doc);
            return true;
        } catch (MongoWriteException e) {
            return false; // lost a race with a duplicate insert
        }
    }

    @Override
    public boolean unblock(UUID owner, UUID target) {
        return blocks.deleteOne(and(eq("owner", owner.toString()), eq("blocked", target.toString()))).getDeletedCount() > 0;
    }

    @Override
    public int unblockAll(UUID owner) {
        return (int) blocks.deleteMany(eq("owner", owner.toString())).getDeletedCount();
    }
}