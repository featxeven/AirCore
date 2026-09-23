package com.ftxeven.aircore.module.homes;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.StoredLocations;
import com.ftxeven.aircore.module.teleport.DestinationSource;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class HomeTeleporter {

    private HomeTeleporter() {
    }

    // Own homes: /home, /home <name>

    public static void browse(Messenger messenger, ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                              Player player, Runnable onSuccess) {
        List<Home> ownHomes = homes.get().findAll(player.getUniqueId());
        if (ownHomes.isEmpty()) {
            fallbackToBed(messenger, configs, services, homes, teleport, player, onSuccess);
            return;
        }
        if (ownHomes.size() == 1) {
            toNamedHome(messenger, configs, services, homes, teleport, player, ownHomes.getFirst(), onSuccess);
            return;
        }
        list(messenger, configs, player, ownHomes, Map.of());
    }

    public static void teleportNamed(Messenger messenger, ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                                     Player player, String name, Runnable onSuccess) {
        Optional<Home> home = homes.get().find(player.getUniqueId(), name);
        if (home.isEmpty()) {
            messenger.send(player, configs.lang().get("homes.errors.not-found"), Map.of("name", name));
            return;
        }
        toNamedHome(messenger, configs, services, homes, teleport, player, home.get(), onSuccess);
    }

    private static void fallbackToBed(Messenger messenger, ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                                      Player player, Runnable onSuccess) {
        Optional<Position> bed = homes.get().bedFallback(player);
        if (bed.isEmpty()) {
            messenger.send(player, configs.lang().get("homes.list.empty"));
            return;
        }
        to(messenger, configs, services, homes, teleport, player, bed.get(),
                "homes.teleported-bed", "homes.cancelled-bed", Map.of(), onSuccess);
    }

    private static void toNamedHome(Messenger messenger, ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                                    Player player, Home home, Runnable onSuccess) {
        to(messenger, configs, services, homes, teleport, player, home.position(),
                "homes.teleported", "homes.cancelled", Map.of("name", home.name()), onSuccess);
    }

    // Listing (own homes, or another player's via /playerhome)

    public static void list(Messenger messenger, ConfigManager configs, Player viewer, List<Home> homeList, Map<String, String> extraPlaceholders) {
        String entryTemplate = configs.lang().get("homes.list.entry").getFirst();
        String separator = configs.lang().get("homes.list.separator").getFirst();

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < homeList.size(); i++) {
            if (i > 0) {
                joined.append(separator);
            }
            joined.append(entryTemplate.replace("%name%", homeList.get(i).name()));
        }

        Map<String, String> placeholders = new LinkedHashMap<>(extraPlaceholders);
        placeholders.put("count", String.valueOf(homeList.size()));
        placeholders.put("homes", joined.toString());
        messenger.send(viewer, configs.lang().get("homes.list.header"), placeholders);
    }

    // Raw position teleport

    public static void to(Messenger messenger, ConfigManager configs, ServiceManager services, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                          Player mover, Position position, String successKey, String cancelledKey,
                          Map<String, String> placeholders, Runnable onSuccess) {
        if (homes.get().blocksWorld(mover, position.world())) {
            messenger.send(mover, configs.lang().get("homes.errors.blocked-world"), Map.of("world", position.world()));
            return;
        }

        Location destination = switch (StoredLocations.resolve(position)) {
            case StoredLocations.Resolution.Ready(Location location) -> location;
            case StoredLocations.Resolution.WorldMissing(String world) -> {
                messenger.send(mover, configs.lang().get("errors.general.world-not-found"), Map.of("world", world));
                yield null;
            }
        };
        if (destination == null) {
            return;
        }

        teleport.get().teleportWithCountdown(mover, mover, DestinationSource.fixed(destination), TeleportType.HOME,
                TeleportMessages.session(messenger, configs, services, mover, mover, destination.getWorld(), null,
                        cancelledKey, placeholders, () -> {
                            onSuccess.run();
                            messenger.send(mover, configs.lang().get(successKey), placeholders);
                        }));
    }
}