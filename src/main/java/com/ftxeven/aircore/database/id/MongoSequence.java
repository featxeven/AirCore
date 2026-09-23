package com.ftxeven.aircore.database.id;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public final class MongoSequence {

    private MongoSequence() {
    }

    public static int next(MongoCollection<Document> counters, String key) {
        Document result = counters.findOneAndUpdate(eq("_id", key), Updates.inc("value", 1),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return result.getInteger("value");
    }
}