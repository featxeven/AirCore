package com.ftxeven.aircore.database.id;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
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

    // never lowers the sequence, only makes sure it is at least floor
    public static void raiseTo(MongoCollection<Document> counters, String key, int floor) {
        counters.updateOne(eq("_id", key), Updates.max("value", floor), new UpdateOptions().upsert(true));
    }
}