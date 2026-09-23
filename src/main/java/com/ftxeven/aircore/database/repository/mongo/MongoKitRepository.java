package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.KitRepository;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.util.ItemSerializer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Binary;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

public final class MongoKitRepository implements KitRepository {

    private final MongoCollection<Document> kits;

    public MongoKitRepository(MongoDatabase database, String tablePrefix) {
        this.kits = database.getCollection(tablePrefix + "kits");
    }

    @Override
    public Optional<Kit> find(String name) {
        Document doc = kits.find(eq("_id", name)).first();
        return doc != null ? Optional.of(mapKit(doc)) : Optional.empty();
    }

    @Override
    public List<Kit> findAll() {
        List<Kit> result = new ArrayList<>();
        for (Document doc : kits.find().sort(Sorts.ascending("_id"))) {
            result.add(mapKit(doc));
        }
        return result;
    }

    @Override
    public void save(Kit kit) {
        kits.updateOne(eq("_id", kit.name()), Updates.combine(
                        Updates.set("items", new Binary(ItemSerializer.serializeAll(kit.items()))),
                        Updates.set("oneTime", kit.oneTime()),
                        Updates.set("cooldownSeconds", kit.cooldownSeconds()),
                        Updates.set("dropOnFullInventory", kit.dropOnFullInventory()),
                        Updates.set("exactSlots", kit.exactSlots()),
                        Updates.set("requiresPermission", kit.requiresPermission()),
                        Updates.setOnInsert("createdAt", Date.from(kit.createdAt())),
                        Updates.setOnInsert("createdBy", kit.createdBy() != null ? kit.createdBy().toString() : null)),
                new UpdateOptions().upsert(true));
    }

    @Override
    public boolean delete(String name) {
        return kits.deleteOne(eq("_id", name)).getDeletedCount() > 0;
    }

    private Kit mapKit(Document doc) {
        String createdBy = doc.getString("createdBy");
        Boolean dropOnFullInventory = doc.getBoolean("dropOnFullInventory");
        Boolean exactSlots = doc.getBoolean("exactSlots");
        Boolean requiresPermission = doc.getBoolean("requiresPermission");
        return new Kit(
                doc.getString("_id"),
                ItemSerializer.deserializeAll(doc.get("items", Binary.class).getData()),
                doc.getBoolean("oneTime"),
                doc.getInteger("cooldownSeconds"),
                dropOnFullInventory != null && dropOnFullInventory,
                exactSlots != null && exactSlots,
                requiresPermission == null || requiresPermission,
                doc.getDate("createdAt").toInstant(),
                createdBy != null ? UUID.fromString(createdBy) : null);
    }
}