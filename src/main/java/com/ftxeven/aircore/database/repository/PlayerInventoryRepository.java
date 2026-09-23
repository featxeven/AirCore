package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.PlayerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public interface PlayerInventoryRepository {

    Optional<PlayerInventory> find(UUID uuid);

    void saveContents(UUID uuid, ItemStack[] contents, int heldSlot);

    void saveEnderChest(UUID uuid, ItemStack[] enderChest);

    void delete(UUID uuid);
}