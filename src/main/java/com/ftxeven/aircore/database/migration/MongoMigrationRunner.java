package com.ftxeven.aircore.database.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.List;

public final class MongoMigrationRunner implements MigrationRunner {

    private final JavaPlugin plugin;
    private final MongoDatabase database;
    private final String tablePrefix;
    private final String versionCollection;

    public MongoMigrationRunner(JavaPlugin plugin, MongoDatabase database, String tablePrefix) {
        this.plugin = plugin;
        this.database = database;
        this.tablePrefix = tablePrefix;
        this.versionCollection = tablePrefix + "schema_version";
    }

    @Override
    public int currentVersion() {
        Document latest = database.getCollection(versionCollection)
                .find()
                .sort(new Document("_id", -1))
                .limit(1)
                .first();
        return latest != null ? latest.getInteger("_id") : 0;
    }

    @Override
    public void migrate(List<Migration> available) {
        int current = currentVersion();

        for (Migration migration : available) {
            if (migration.version() <= current) {
                continue;
            }
            apply(migration);
            plugin.getLogger().info("Applied migration V" + migration.version() + " (" + migration.description() + ")");
        }
    }

    private void apply(Migration migration) {
        String content = migration.content().replace("{prefix}", tablePrefix);
        Document spec = Document.parse(content);

        for (Document collectionSpec : spec.getList("collections", Document.class)) {
            MongoCollection<Document> collection = database.getCollection(collectionSpec.getString("name"));

            for (Document index : collectionSpec.getList("indexes", Document.class, List.of())) {
                Document keys = index.get("keys", Document.class);
                Document options = index.get("options", new Document());

                IndexOptions indexOptions = new IndexOptions().unique(options.getBoolean("unique", false));

                String name = options.getString("name");
                if (name != null) {
                    indexOptions.name(name);
                }

                Document collationSpec = options.get("collation", Document.class);
                if (collationSpec != null) {
                    indexOptions.collation(Collation.builder()
                            .locale(collationSpec.getString("locale"))
                            .collationStrength(CollationStrength.fromInt(collationSpec.getInteger("strength")))
                            .build());
                }

                collection.createIndex(keys, indexOptions);
            }
        }

        database.getCollection(versionCollection).insertOne(new Document()
                .append("_id", migration.version())
                .append("description", migration.description())
                .append("appliedAt", new Date()));
    }
}