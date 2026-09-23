package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.NamedLocation;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public final class MongoLocationRepository implements LocationRepository {

    private final MongoCollection<Document> locations;

    public MongoLocationRepository(MongoDatabase database, String tablePrefix) {
        this.locations = database.getCollection(tablePrefix + "locations");
    }

    @Override
    public Optional<NamedLocation> find(Category category, String key) {
        Document doc = locations.find(and(eq("category", category.name()), eq("key", key))).first();
        return doc != null ? Optional.of(mapLocation(doc)) : Optional.empty();
    }

    @Override
    public List<NamedLocation> findAll(Category category) {
        List<NamedLocation> result = new ArrayList<>();
        for (Document doc : locations.find(eq("category", category.name())).sort(Sorts.ascending("key"))) {
            result.add(mapLocation(doc));
        }
        return result;
    }

    @Override
    public void save(Category category, NamedLocation location) {
        locations.updateOne(and(eq("category", category.name()), eq("key", location.key())),
                Updates.combine(
                        Updates.set("position", positionDoc(location.position())),
                        Updates.setOnInsert("createdAt", Date.from(location.createdAt())),
                        Updates.setOnInsert("createdBy", location.createdBy() != null ? location.createdBy().toString() : null)),
                new UpdateOptions().upsert(true));
    }

    @Override
    public boolean delete(Category category, String key) {
        return locations.deleteOne(and(eq("category", category.name()), eq("key", key))).getDeletedCount() > 0;
    }

    private Document positionDoc(Position position) {
        return new Document("world", position.world())
                .append("x", position.x()).append("y", position.y()).append("z", position.z())
                .append("yaw", (double) position.yaw()).append("pitch", (double) position.pitch());
    }

    private NamedLocation mapLocation(Document doc) {
        Document pos = doc.get("position", Document.class);
        Position position = new Position(pos.getString("world"),
                pos.get("x", Number.class).doubleValue(), pos.get("y", Number.class).doubleValue(), pos.get("z", Number.class).doubleValue(),
                pos.get("yaw", Number.class).floatValue(), pos.get("pitch", Number.class).floatValue());
        String createdBy = doc.getString("createdBy");
        return new NamedLocation(doc.getString("key"), position, doc.getDate("createdAt").toInstant(),
                createdBy != null ? UUID.fromString(createdBy) : null);
    }
}