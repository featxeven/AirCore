package com.ftxeven.aircore.service.inventory;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class InventoryHandle {

    private final InventoryService service;
    private final UUID viewer;
    private final UUID target;
    private final InventoryKind kind;
    private final boolean canModify;
    private volatile boolean closed;

    InventoryHandle(InventoryService service, UUID viewer, UUID target, InventoryKind kind, boolean canModify) {
        this.service = service;
        this.viewer = viewer;
        this.target = target;
        this.kind = kind;
        this.canModify = canModify;
    }

    public UUID viewer() { return viewer; }
    public UUID target() { return target; }
    public InventoryKind kind() { return kind; }
    public boolean canModify() { return canModify; }

    public CompletableFuture<Boolean> isTargetOnline() {
        return service.isLive(target, kind);
    }

    public CompletableFuture<Integer> size() {
        return service.size(target, kind);
    }

    public CompletableFuture<ItemStack[]> snapshot() {
        return service.snapshot(target, kind);
    }

    public CompletableFuture<ItemStack> get(int slot) {
        return service.get(target, kind, slot);
    }

    public CompletableFuture<Boolean> set(int slot, @Nullable ItemStack item) {
        if (!canModify) {
            return CompletableFuture.completedFuture(false);
        }
        return service.set(target, kind, slot, item, viewer);
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        service.release(target, kind, viewer);
    }

    public record SlotChange(int slot, @Nullable ItemStack item, @Nullable UUID changedBy) {}

    public record Opened(InventoryHandle handle, ItemStack[] initialContents) {}
}