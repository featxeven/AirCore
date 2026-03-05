package com.ftxeven.aircore.api.event.block;

import java.util.UUID;

public final class PlayerBlockEvent extends BlockEvent {
    public PlayerBlockEvent(UUID player, UUID target) {
        super(player, target);
    }
}