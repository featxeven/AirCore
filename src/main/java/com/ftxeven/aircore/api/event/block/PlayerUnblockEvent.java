package com.ftxeven.aircore.api.event.block;

import java.util.UUID;

public final class PlayerUnblockEvent extends BlockEvent {
    public PlayerUnblockEvent(UUID player, UUID target) {
        super(player, target);
    }
}