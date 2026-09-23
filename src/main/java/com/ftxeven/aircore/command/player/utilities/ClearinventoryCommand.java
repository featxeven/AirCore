package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;

public final class ClearinventoryCommand extends LiveActionCommand {

    public ClearinventoryCommand(Context ctx) {
        super(ctx, "clearinventory", new Keys("utilities.clear-inventory.self", "utilities.clear-inventory.other",
                "utilities.clear-inventory.by", "utilities.clear-inventory.all", "utilities.clear-inventory.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isEmpty(inventory.getStorageContents()) && isEmpty(inventory.getArmorContents()) && isAir(inventory.getItemInOffHand())) {
            return Optional.of("already-empty");
        }
        inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
        inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        inventory.setItemInOffHand(null);
        return Optional.empty();
    }

    private static boolean isEmpty(ItemStack[] items) {
        for (ItemStack item : items) {
            if (!isAir(item)) return false;
        }
        return true;
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}