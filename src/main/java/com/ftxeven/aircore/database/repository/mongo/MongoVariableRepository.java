package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.VariableRepository;
import com.ftxeven.aircore.model.Variable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;

public final class MongoVariableRepository implements VariableRepository {

    private final MongoCollection<Document> variables;

    public MongoVariableRepository(MongoDatabase database, String tablePrefix) {
        this.variables = database.getCollection(tablePrefix + "variables");
    }

    @Override
    public Map<String, String> loadGlobal() {
        return load(GLOBAL_OWNER);
    }

    @Override
    public Map<String, String> loadPlayer(UUID owner) {
        return load(owner.toString());
    }

    private Map<String, String> load(String owner) {
        Map<String, String> values = new HashMap<>();
        for (Document doc : variables.find(eq("owner", owner)).projection(include("key", "value"))) {
            values.put(doc.getString("key"), doc.getString("value"));
        }
        return values;
    }

    @Override
    public void apply(List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        UpdateOptions upsert = new UpdateOptions().upsert(true);
        List<WriteModel<Document>> models = new ArrayList<>(changes.size());
        for (Change change : changes) {
            Bson filter = and(eq("owner", change.owner()), eq("key", change.key()));
            if (change.value() == null) {
                models.add(new DeleteOneModel<>(filter));
                continue;
            }
            Bson numeric = change.numeric() == null ? Updates.unset("numeric") : Updates.set("numeric", change.numeric());
            models.add(new UpdateOneModel<>(filter, Updates.combine(Updates.set("value", change.value()), numeric), upsert));
        }
        variables.bulkWrite(models, new BulkWriteOptions().ordered(false));
    }

    @Override
    public int deleteAll(UUID owner) {
        return (int) variables.deleteMany(eq("owner", owner.toString())).getDeletedCount();
    }

    @Override
    public int purgeOrphaned(Collection<String> knownKeys) {
        return (int) variables.deleteMany(knownKeys.isEmpty() ? new Document() : nin("key", knownKeys)).getDeletedCount();
    }

    @Override
    public int backfillNumeric(String key) {
        Document convert = new Document("$convert", new Document("input", "$value")
                .append("to", "double").append("onError", null).append("onNull", null));
        return (int) variables.updateMany(
                and(eq("key", key), exists("numeric", false)),
                List.of(new Document("$set", new Document("numeric", convert)))
        ).getModifiedCount();
    }

    @Override
    public List<Variable> top(String key, boolean descending, int limit, double minValue) {
        Bson sort = descending ? Sorts.descending("numeric", "owner") : Sorts.ascending("numeric", "owner");
        FindIterable<Document> find = variables
                .find(and(eq("key", key), gte("numeric", minValue), ne("owner", GLOBAL_OWNER)))
                .projection(include("owner", "value", "numeric"))
                .sort(sort);
        if (limit > 0) {
            find = find.limit(limit);
        }
        List<Variable> ranked = new ArrayList<>();
        for (Document doc : find) {
            try {
                UUID owner = UUID.fromString(doc.getString("owner"));
                ranked.add(new Variable(owner, key, doc.getString("value"), doc.getDouble("numeric")));
            } catch (IllegalArgumentException ignored) {
                // malformed owner id, not a player row
            }
        }
        return ranked;
    }
}