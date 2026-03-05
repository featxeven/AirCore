package com.ftxeven.aircore.api.manager;

import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.UUID;

public interface BlockManager {
    boolean isBlocked(@NotNull UUID player, @NotNull UUID target);
    @NotNull Set<UUID> getBlocked(@NotNull UUID player);
    void block(@NotNull UUID player, @NotNull UUID target);
    void unblock(@NotNull UUID player, @NotNull UUID target);
}