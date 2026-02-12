package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.AirCore;
import org.bukkit.OfflinePlayer;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class PlaceholderManager {

    private final AirCore plugin;
    private final PlaceholderService service;
    private final PlaceholderLoader loader;
    private Map<String, PlaceholderLoader.PlaceholderEntry> registry = new HashMap<>();

    public PlaceholderManager(AirCore plugin) {
        this.plugin = plugin;
        this.service = new PlaceholderService();
        this.loader = new PlaceholderLoader(plugin);

        setupInitialFiles();
        reload();
    }

    private void setupInitialFiles() {
        File folder = new File(plugin.getDataFolder(), "placeholders");

        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("placeholders/example.yml", false);
        }
    }

    public void reload() {
        this.registry = loader.loadAll();
    }

    public String resolve(OfflinePlayer player, String key) {
        PlaceholderLoader.PlaceholderEntry entry = registry.get(key.toLowerCase());
        if (entry == null) return null;
        return service.resolve(player, entry);
    }
}