package com.ftxeven.aircore.module.core.home;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public final class HomeService {

    private final AirCore plugin;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();

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

    public int getLimit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return plugin.config().homesMaxHomes();
        }

        int limit = plugin.config().homesMaxHomes();

        if (player.hasPermission("aircore.bypass.home.limit.*")) {
            return Integer.MAX_VALUE;
        }
        for (int i = 1; i <= 100; i++) {
            if (player.hasPermission("aircore.bypass.home.limit." + i)) {
                limit = Math.max(limit, i);
            }
        }

        return limit;
    }

    public Result setHome(Player player, String name) {
        if (plugin.config().homesAllowNames()) {
            String regex = plugin.config().homesNameValidationRegex();
            if (!name.matches(regex)) {
                return new Result(Status.INVALID_NAME, name);
            }
            if (name.length() > plugin.config().homesMaxNameLength()) {
                return new Result(Status.NAME_TOO_LONG, name);
            }
        } else {
            Map<String, Location> homes = getHomes(player.getUniqueId());
            Set<Integer> existing = new HashSet<>();
            for (String key : homes.keySet()) {
                try {
                    existing.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {}
            }
            int next = 1;
            while (existing.contains(next)) {
                next++;
            }
            name = String.valueOf(next);
        }

        String worldName = player.getWorld().getName().toLowerCase();
        boolean isDisabledWorld = plugin.config().homesDisabledWorlds().stream()
                .map(String::toLowerCase)
                .anyMatch(disabled -> disabled.equals(worldName));

        if (isDisabledWorld && !player.hasPermission("aircore.bypass.home.worlds")) {
            return new Result(Status.DISABLED_WORLD, name);
        }

        int maxHomes = getLimit(player.getUniqueId());
        Map<String, Location> homes = getHomes(player.getUniqueId());

        if (homes.containsKey(name)) {
            return new Result(Status.ALREADY_EXISTS, name);
        }
        if (homes.size() >= maxHomes) {
            return new Result(Status.LIMIT_REACHED, name);
        }

        Location loc = player.getLocation();
        homes.put(name, loc);

        final UUID uuidFinal = player.getUniqueId();
        final String nameFinal = name;
        final Location locFinal = loc;

        plugin.scheduler().runEntityTask(player, () -> {
            try {
                plugin.database().homes().save(uuidFinal, nameFinal, locFinal);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save home '" + nameFinal + "' for " + uuidFinal, e);
            }
        });

        return new Result(Status.SUCCESS, name);
    }

    public void deleteHome(UUID uuid, String homeName) {
        var homes = getHomes(uuid);
        homes.remove(homeName);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.scheduler().runEntityTask(player, () -> {
                try {
                    plugin.database().homes().delete(uuid, homeName);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to delete home '" + homeName + "' for " + uuid, e);
                }
            });
        } else {
            plugin.scheduler().runAsync(() -> {
                try {
                    plugin.database().homes().delete(uuid, homeName);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to delete home '" + homeName + "' for " + uuid, e);
                }
            });
        }
    }

    public Map<String, Location> getHomes(UUID playerId) {
        return playerHomes.computeIfAbsent(playerId, id -> {
            Map<String, Location> loaded = plugin.database().homes().load(id);
            return new HashMap<>(loaded);
        });
    }

    public void loadFromDatabase(UUID uuid, Map<String, Location> homes) {
        playerHomes.put(uuid, new HashMap<>(homes));
    }
}