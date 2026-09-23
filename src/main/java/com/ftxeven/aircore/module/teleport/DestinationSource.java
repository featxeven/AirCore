package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@FunctionalInterface
public interface DestinationSource {

    void resolve(Consumer<Optional<Location>> callback);

    static DestinationSource fixed(Location location) {
        return callback -> callback.accept(Optional.of(location));
    }

    static DestinationSource ofPlayer(UUID uuid) {
        return callback -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                callback.accept(Optional.empty());
                return;
            }
            Scheduler.runEntity(player, () -> callback.accept(Optional.of(player.getLocation().clone())));
        };
    }
}