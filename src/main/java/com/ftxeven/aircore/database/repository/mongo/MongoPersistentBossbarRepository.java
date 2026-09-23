package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.PersistentBossbarRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

public final class MongoPersistentBossbarRepository implements PersistentBossbarRepository {

    private final MongoCollection<Document> collection;

    public MongoPersistentBossbarRepository(MongoDatabase database, String tablePrefix) {
        this.collection = database.getCollection(tablePrefix + "persistent_bossbar");
    }

    @Override
    public List<State> findAll() {
        List<State> results = new ArrayList<>();
        for (Document doc : collection.find()) {
            results.add(mapState(doc));
        }
        return results;
    }

    @Override
    public void save(State state) {
        Document placeholdersDoc = new Document();
        state.placeholders().forEach(placeholdersDoc::append);

        Document doc = new Document("_id", state.key())
                .append("text", state.text())
                .append("durationSeconds", state.durationSeconds())
                .append("color", state.color())
                .append("overlay", state.overlay())
                .append("countdown", state.countdown())
                .append("initialProgress", state.initialProgress())
                .append("startedAt", state.startedAtEpochMillis())
                .append("placeholders", placeholdersDoc)
                .append("syncOnJoin", state.syncOnJoin())
                .append("force", state.force())
                .append("conditions", state.conditions());
        collection.replaceOne(eq("_id", state.key()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public void clear(String key) {
        collection.deleteOne(eq("_id", key));
    }

    private State mapState(Document doc) {
        Document placeholdersDoc = doc.get("placeholders", Document.class);
        Map<String, String> placeholders = new LinkedHashMap<>();
        if (placeholdersDoc != null) {
            placeholdersDoc.forEach((key, value) -> placeholders.put(key, String.valueOf(value)));
        }

        List<String> conditions = doc.getList("conditions", String.class);

        return new State(
                doc.getString("_id"),
                doc.getString("text"),
                doc.getInteger("durationSeconds"),
                doc.getString("color"),
                doc.getString("overlay"),
                doc.getBoolean("countdown"),
                doc.get("initialProgress", Number.class).doubleValue(),
                doc.get("startedAt", Number.class).longValue(),
                placeholders,
                doc.getBoolean("syncOnJoin", true),
                doc.getBoolean("force", false),
                conditions != null ? new ArrayList<>(conditions) : List.of()
        );
    }
}