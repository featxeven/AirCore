package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.HomeRepository;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

public final class MongoHomeRepository implements HomeRepository {

    private final MongoCollection<Document> homes;

    public MongoHomeRepository(MongoDatabase database, String tablePrefix) {
        this.homes = database.getCollection(tablePrefix + "homes");
    }

    @Override
    public Optional<Home> find(UUID owner, String name) {
        Document doc = homes.find(and(eq("owner", owner.toString()), eq("name", name))).first();
        return doc != null ? Optional.of(mapHome(doc)) : Optional.empty();
    }

    @Override
    public List<Home> findAll(UUID owner) {
        List<Home> result = new ArrayList<>();
        for (Document doc : homes.find(eq("owner", owner.toString())).sort(Sorts.ascending("name"))) {
            result.add(mapHome(doc));
        }
        return result;
    }

    @Override
    public int count(UUID owner) {
        return (int) homes.countDocuments(eq("owner", owner.toString()));
    }

    @Override
    public Home save(Home home) {
        Bson filter = and(eq("owner", home.owner().toString()), eq("name", home.name()));
        Bson update = Updates.combine(
                Updates.set("position", positionDoc(home.position())),
                Updates.set("icon", home.icon()),
                Updates.set("favorite", home.favorite()),
                Updates.setOnInsert("createdAt", Date.from(home.createdAt())));
        homes.updateOne(filter, update, new UpdateOptions().upsert(true));
        return home;
    }

    @Override
    public boolean setFavorite(UUID owner, String name, boolean favorite) {
        Bson filter = and(eq("owner", owner.toString()), eq("name", name));
        return homes.updateOne(filter, Updates.set("favorite", favorite)).getModifiedCount() > 0;
    }

    @Override
    public boolean setIcon(UUID owner, String name, @Nullable String icon) {
        Bson filter = and(eq("owner", owner.toString()), eq("name", name));
        return homes.updateOne(filter, Updates.set("icon", icon)).getModifiedCount() > 0;
    }

    @Override
    public boolean delete(UUID owner, String name) {
        return homes.deleteOne(and(eq("owner", owner.toString()), eq("name", name))).getDeletedCount() > 0;
    }

    @Override
    public int deleteAll(UUID owner) {
        return (int) homes.deleteMany(eq("owner", owner.toString())).getDeletedCount();
    }

    @Override
    public int deleteAll(Collection<UUID> owners) {
        if (owners.isEmpty()) {
            return 0;
        }
        List<String> ids = owners.stream().map(UUID::toString).toList();
        return (int) homes.deleteMany(in("owner", ids)).getDeletedCount();
    }

    // Row mapping

    private Document positionDoc(Position position) {
        return new Document("world", position.world())
                .append("x", position.x()).append("y", position.y()).append("z", position.z())
                .append("yaw", (double) position.yaw()).append("pitch", (double) position.pitch());
    }

    private Home mapHome(Document doc) {
        Document pos = doc.get("position", Document.class);
        Position position = new Position(pos.getString("world"),
                pos.get("x", Number.class).doubleValue(), pos.get("y", Number.class).doubleValue(), pos.get("z", Number.class).doubleValue(),
                pos.get("yaw", Number.class).floatValue(), pos.get("pitch", Number.class).floatValue());
        Boolean favorite = doc.getBoolean("favorite");
        return new Home(UUID.fromString(doc.getString("owner")), doc.getString("name"), position,
                doc.getString("icon"), favorite != null && favorite, doc.getDate("createdAt").toInstant());
    }
}