package com.ftxeven.aircore.api.event;

import java.util.UUID;

public final class PlayerBlockEvent extends AirCoreBlockBaseEvent {
    public PlayerBlockEvent(UUID player, UUID target) {
        super(player, target);
    }
}