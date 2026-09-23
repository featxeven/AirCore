package com.ftxeven.aircore.database.repository.mongo;

import com.ftxeven.aircore.database.repository.PlayerInventoryRepository;
import com.ftxeven.aircore.model.PlayerInventory;
import com.ftxeven.aircore.util.ItemSerializer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Binary;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

public final class MongoPlayerInventoryRepository implements PlayerInventoryRepository {

    private final MongoCollection<Document> inventories;

    public MongoPlayerInventoryRepository(MongoDatabase database, String tablePrefix) {
        this.inventories = database.getCollection(tablePrefix + "player_inventories");
    }

    @Override
    public Optional<PlayerInventory> find(UUID uuid) {
        Document doc = inventories.find(eq("_id", uuid.toString())).first();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerInventory(
                uuid,
                ItemSerializer.deserializeAll(doc.get("contents", Binary.class).getData()),
                ItemSerializer.deserializeAll(doc.get("enderChest", Binary.class).getData()),
                doc.getInteger("heldSlot")));
    }

    @Override
    public void saveContents(UUID uuid, ItemStack[] contents, int heldSlot) {
        inventories.updateOne(eq("_id", uuid.toString()), Updates.combine(
                        Updates.set("contents", new Binary(ItemSerializer.serializeAll(contents))),
                        Updates.set("heldSlot", heldSlot),
                        Updates.setOnInsert("enderChest", new Binary(ItemSerializer.serializeAll(new ItemStack[PlayerInventory.ENDERCHEST_SIZE])))),
                new UpdateOptions().upsert(true));
    }

    @Override
    public void saveEnderChest(UUID uuid, ItemStack[] enderChest) {
        inventories.updateOne(eq("_id", uuid.toString()), Updates.combine(
                        Updates.set("enderChest", new Binary(ItemSerializer.serializeAll(enderChest))),
                        Updates.setOnInsert("contents", new Binary(ItemSerializer.serializeAll(new ItemStack[PlayerInventory.MAIN_SIZE]))),
                        Updates.setOnInsert("heldSlot", 0)),
                new UpdateOptions().upsert(true));
    }

    @Override
    public void delete(UUID uuid) {
        inventories.deleteOne(eq("_id", uuid.toString()));
    }
}