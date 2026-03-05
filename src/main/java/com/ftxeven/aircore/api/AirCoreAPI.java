package com.ftxeven.aircore.api;

import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.UUID;

public interface AirCoreAPI {

    /**
     * Gets the Block Manager to handle player block relationships.
     */
    @NotNull BlockManager getBlockManager();

    interface BlockManager {
        /**
         * Checks if a player has blocked another.
         */
        boolean isBlocked(@NotNull UUID player, @NotNull UUID target);

        /**
         * Gets a read-only set of blocked UUIDs for a player.
         */
        @NotNull Set<UUID> getBlockedPlayers(@NotNull UUID player);

        /**
         * Blocks a player.
         */
        void block(@NotNull UUID player, @NotNull UUID target);

        /**
         * Unblocks a player.
         */
        void unblock(@NotNull UUID player, @NotNull UUID target);
    }
}