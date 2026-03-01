package com.ftxeven.aircore.core.modules.home;

import com.ftxeven.aircore.AirCore;
import net.luckperms.api.LuckPerms;
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

        if (player != null) {
            if (player.hasPermission("aircore.bypass.home.limit.*")) return Integer.MAX_VALUE;

            int highest = 0;
            for (var perm : player.getEffectivePermissions()) {
                if (perm.getValue() && perm.getPermission().startsWith("aircore.bypass.home.limit.")) {
                    try {
                        int value = Integer.parseInt(perm.getPermission().substring("aircore.bypass.home.limit.".length()));
                        highest = Math.max(highest, value);
                    } catch (NumberFormatException ignored) {}
                }
            }
            return highest > 0 ? highest : plugin.config().homesMaxHomes();
        }

        try {
            var luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
            if (luckPerms != null) {
                var user = luckPerms.getUserManager().getUser(uuid);

                if (user == null) {
                    luckPerms.getUserManager().loadUser(uuid);
                    return plugin.config().homesMaxHomes();
                }

                var bypass = user.getCachedData().getPermissionData().checkPermission("aircore.bypass.home.limit.*");
                if (bypass.asBoolean()) return Integer.MAX_VALUE;

                return user.getNodes().stream()
                        .filter(node -> node.getValue() && node.getKey().startsWith("aircore.bypass.home.limit."))
                        .map(node -> node.getKey().substring("aircore.bypass.home.limit.".length()))
                        .mapToInt(s -> {
                            try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
                        })
                        .max()
                        .orElse(plugin.config().homesMaxHomes());
            }
        } catch (Exception ignored) {}

        return plugin.config().homesMaxHomes();
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
        Map<String, Location> existing = getHomes(uuid);
        existing.putAll(loadedHomes);
    }

    public void unload(UUID uuid) {
        playerHomes.remove(uuid);
    }
}