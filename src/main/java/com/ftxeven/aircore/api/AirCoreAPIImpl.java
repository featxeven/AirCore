package com.ftxeven.aircore.core.api;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.api.AirCoreAPI;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public final class AirCoreAPIImpl implements AirCoreAPI {

    private final AirCore plugin;
    private final BlockManagerImpl blockManager;

    public AirCoreAPIImpl(AirCore plugin) {
        this.plugin = plugin;
        this.blockManager = new BlockManagerImpl();
    }

    @Override
    public @NotNull BlockManager getBlockManager() {
        return blockManager;
    }

    private final class BlockManagerImpl implements BlockManager {
        @Override
        public boolean isBlocked(@NotNull UUID player, @NotNull UUID target) {
            return plugin.core().blocks().isBlocked(player, target);
        }

        @Override
        public @NotNull Set<UUID> getBlockedPlayers(@NotNull UUID player) {
            return plugin.core().blocks().getBlocked(player);
        }

        @Override
        public void block(@NotNull UUID player, @NotNull UUID target) {
            // This triggers the internal logic (limits, cache, and DB)
            plugin.core().blocks().block(player, target);
            plugin.scheduler().runAsync(() -> plugin.database().blocks().add(player, target));
        }

        @Override
        public void unblock(@NotNull UUID player, @NotNull UUID target) {
            plugin.core().blocks().unblock(player, target);
            plugin.scheduler().runAsync(() -> plugin.database().blocks().remove(player, target));
        }
    }
}