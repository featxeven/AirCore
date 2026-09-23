package com.ftxeven.aircore.service.inventory;

import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class LiveInventoryWatcher {

    private static final long PERIOD_TICKS = 1L;

    private final Player player;
    private final Supplier<Inventory> container;
    private final BiConsumer<Integer, ItemStack> onSlotChanged;

    private @Nullable ItemStack[] lastSeen;
    private volatile @Nullable ScheduledTask task;
    private volatile boolean stopped;

    LiveInventoryWatcher(Player player, Supplier<Inventory> container, BiConsumer<Integer, ItemStack> onSlotChanged) {
        this.player = player;
        this.container = container;
        this.onSlotChanged = onSlotChanged;
    }

    void start() {
        Scheduler.runEntity(player, this::begin);
    }

    private void begin() {
        if (stopped) {
            return;
        }
        lastSeen = snapshot();
        schedule();
    }

    private void schedule() {
        if (stopped) {
            return;
        }
        ScheduledTask scheduled = Scheduler.runEntityTimer(player, this::poll, this::onRetired, PERIOD_TICKS, PERIOD_TICKS).orElse(null);
        task = scheduled;
        if (stopped && scheduled != null) {
            scheduled.cancel();
        }
    }

    private void onRetired() {
        if (!stopped) {
            schedule();
        }
    }

    void stop() {
        stopped = true;
        ScheduledTask current = task;
        task = null;
        if (current != null) {
            current.cancel();
        }
    }

    void acknowledge(int slot, @Nullable ItemStack item) {
        if (lastSeen != null && slot >= 0 && slot < lastSeen.length) {
            lastSeen[slot] = ItemStackSync.clone(item);
        }
    }

    private void poll() {
        if (lastSeen == null) {
            return;
        }
        Inventory inventory = container.get();
        int size = Math.min(lastSeen.length, inventory.getSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!ItemStackSync.matches(current, lastSeen[slot])) {
                ItemStack clone = ItemStackSync.clone(current);
                lastSeen[slot] = clone;
                onSlotChanged.accept(slot, clone);
            }
        }
    }

    private ItemStack[] snapshot() {
        Inventory inventory = container.get();
        ItemStack[] copy = new ItemStack[inventory.getSize()];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = ItemStackSync.clone(inventory.getItem(i));
        }
        return copy;
    }
}