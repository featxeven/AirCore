package com.ftxeven.aircore.core.api;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.api.AirCoreAPI;
import com.ftxeven.aircore.api.manager.BlockManager;
import com.ftxeven.aircore.api.event.block.PlayerBlockEvent;
import com.ftxeven.aircore.api.event.block.PlayerUnblockEvent;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public final class DefaultAirCoreAPI implements AirCoreAPI {

    private final AirCore plugin;
    private final BlockManager blockManager;

    public DefaultAirCoreAPI(AirCore plugin) {
        this.plugin = plugin;
        this.blockManager = new BlockManagerImpl();
    }

    @Override public @NotNull BlockManager blocks() { return blockManager; }

    private final class BlockManagerImpl implements BlockManager {
        @Override
        public boolean isBlocked(@NotNull UUID player, @NotNull UUID target) {
            return plugin.core().blocks().isBlocked(player, target);
        }

        @Override
        public @NotNull Set<UUID> getBlocked(@NotNull UUID player) {
            return plugin.core().blocks().getBlocked(player);
        }

        @Override
        public void block(@NotNull UUID player, @NotNull UUID target) {
            if (isBlocked(player, target)) return;
            plugin.core().blocks().block(player, target);
            plugin.scheduler().runAsync(() -> plugin.database().blocks().add(player, target));
            Bukkit.getPluginManager().callEvent(new PlayerBlockEvent(player, target));
        }

        @Override
        public void unblock(@NotNull UUID player, @NotNull UUID target) {
            if (!isBlocked(player, target)) return;
            plugin.core().blocks().unblock(player, target);
            plugin.scheduler().runAsync(() -> plugin.database().blocks().remove(player, target));
            Bukkit.getPluginManager().callEvent(new PlayerUnblockEvent(player, target));
        }
    }
}