package com.ftxeven.aircore.api.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public abstract class BlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final UUID target;

    public BlockEvent(UUID player, UUID target) {
        this.player = player;
        this.target = target;
    }

    public UUID getPlayer() { return player; }
    public UUID getTarget() { return target; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}