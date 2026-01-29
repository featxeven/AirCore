package com.ftxeven.aircore.module.core.home;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class HomeManager {

    private final AirCore plugin;
    private HomeService homeService;

    public HomeManager(AirCore plugin) {
        this.plugin = plugin;
        constructServices();
        preloadOnlinePlayers();
    }

    public void reload() {
        constructServices();
        preloadOnlinePlayers();
    }

    private void constructServices() {
        this.homeService = new HomeService(plugin);
    }

    public HomeService homes() {
        return homeService;
    }

    private void preloadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            final UUID playerUuid = player.getUniqueId();
            plugin.scheduler().runAsync(() -> {
                Map<String, Location> homes =
                        plugin.database().homes().load(playerUuid);
                homeService.loadFromDatabase(playerUuid, homes);
            });
        }
    }
}