package com.ftxeven.aircore.api.event;

import java.util.UUID;

public final class PlayerUnblockEvent extends AirCoreBlockBaseEvent {
    public PlayerUnblockEvent(UUID player, UUID target) {
        super(player, target);
    }
}