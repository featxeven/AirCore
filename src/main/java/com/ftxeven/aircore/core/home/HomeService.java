package com.ftxeven.aircore.core.home;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class HomeService {

    private final AirCore plugin;
    private final Map<UUID, Map<String, Location>> playerHomes = new ConcurrentHashMap<>();

    public HomeService(AirCore plugin) {
        this.plugin = plugin;
    }

    public record Result(Status status, String homeName) {}

    public enum Status {
        SUCCESS,
        INVALID_NAME,
        NAME_TOO_LONG,
        DISABLED_WORLD,
        ALREADY_EXISTS,
        LIMIT_REACHED,
        NOT_FOUND
    }

    public Map<String, Location> getHomes(UUID playerId) {
        return playerHomes.computeIfAbsent(playerId, id ->
                Collections.synchronizedMap(new LinkedHashMap<>()));
    }

    public int getLimit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return plugin.config().homesMaxHomes();

        if (player.hasPermission("aircore.bypass.home.limit.*")) {
            return Integer.MAX_VALUE;
        }

        int limit = plugin.config().homesMaxHomes();
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("aircore.bypass.home.limit." + i)) {
                limit = Math.max(limit, i);
                break;
            }
        }
        return limit;
    }

    public Result setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = getHomes(uuid);

        if (plugin.config().homesAllowNames()) {
            String regex = plugin.config().homesNameValidationRegex();
            if (!name.matches(regex)) return new Result(Status.INVALID_NAME, name);
            if (name.length() > plugin.config().homesMaxNameLength()) return new Result(Status.NAME_TOO_LONG, name);
        } else {
            int next = 1;
            while (homes.containsKey(String.valueOf(next))) next++;
            name = String.valueOf(next);
        }

        String worldName = player.getWorld().getName().toLowerCase();
        boolean isDisabled = plugin.config().homesDisabledWorlds().stream()
                .anyMatch(w -> w.equalsIgnoreCase(worldName));

        if (isDisabled && !player.hasPermission("aircore.bypass.home.worlds")) {
            return new Result(Status.DISABLED_WORLD, name);
        }

        if (homes.containsKey(name)) return new Result(Status.ALREADY_EXISTS, name);
        if (homes.size() >= getLimit(uuid)) return new Result(Status.LIMIT_REACHED, name);

        Location loc = player.getLocation();
        homes.put(name, loc);

        final String finalName = name;
        plugin.scheduler().runAsync(() -> {
            try {
                plugin.database().homes().save(uuid, finalName, loc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save home '" + finalName + "' for " + uuid, e);
            }
        });

        return new Result(Status.SUCCESS, name);
    }

    public void deleteHome(UUID uuid, String homeName) {
        Map<String, Location> homes = playerHomes.get(uuid);
        if (homes != null) homes.remove(homeName);

        plugin.scheduler().runAsync(() -> {
            try {
                plugin.database().homes().delete(uuid, homeName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete home '" + homeName + "' for " + uuid, e);
            }
        });
    }

    public void loadFromDatabase(UUID uuid, Map<String, Location> loadedHomes) {
        playerHomes.put(uuid, Collections.synchronizedMap(new LinkedHashMap<>(loadedHomes)));
    }

    public void unload(UUID uuid) {
        playerHomes.remove(uuid);
    }
}