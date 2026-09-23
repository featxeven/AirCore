package com.ftxeven.aircore.service.item;

import com.ftxeven.aircore.util.ItemDelivery;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldingService {

    private final Map<UUID, Map<HoldingKind, ItemStack[]>> held = new ConcurrentHashMap<>();

    public ItemStack[] snapshot(UUID uuid, HoldingKind kind, int size) {
        ItemStack[] stored = heldOf(uuid).get(kind);
        ItemStack[] copy = new ItemStack[size];
        if (stored != null) {
            for (int i = 0; i < Math.min(stored.length, size); i++) {
                copy[i] = clone(stored[i]);
            }
        }
        return copy;
    }

    public void store(UUID uuid, HoldingKind kind, ItemStack[] contents) {
        if (isEmpty(contents)) {
            clear(uuid, kind);
            return;
        }
        held.computeIfAbsent(uuid, key -> new EnumMap<>(HoldingKind.class)).put(kind, cloneAll(contents));
    }

    public void clear(UUID uuid, HoldingKind kind) {
        held.computeIfPresent(uuid, (key, byKind) -> {
            byKind.remove(kind);
            return byKind.isEmpty() ? null : byKind;
        });
    }

    public int count(UUID uuid, HoldingKind kind) {
        return count(heldOf(uuid).get(kind));
    }

    public void returnAll(Player player, HoldingKind kind) {
        UUID uuid = player.getUniqueId();
        ItemStack[] contents = heldOf(uuid).get(kind);
        clear(uuid, kind);
        giveBack(player, contents);
    }

    public void handleQuit(Player player) {
        Map<HoldingKind, ItemStack[]> byKind = held.remove(player.getUniqueId());
        if (byKind == null) {
            return;
        }
        for (ItemStack[] contents : byKind.values()) {
            giveBack(player, contents);
        }
    }

    private void giveBack(Player player, @Nullable ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (ItemStack item : contents) {
            if (!isEmpty(item)) {
                ItemDelivery.give(player, item, item.getAmount(), true);
            }
        }
    }

    private Map<HoldingKind, ItemStack[]> heldOf(UUID uuid) {
        return held.getOrDefault(uuid, Map.of());
    }

    private static int count(@Nullable ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static boolean isEmpty(@Nullable ItemStack[] items) {
        return items == null || count(items) == 0;
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = clone(items[i]);
        }
        return copy;
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }
}