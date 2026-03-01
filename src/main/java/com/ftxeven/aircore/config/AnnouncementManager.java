package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.AnnouncementService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnnouncementManager {
    private final AirCore plugin;
    private final AnnouncementService service;
    private final Map<String, ConfigurationSection> registry = new ConcurrentHashMap<>();
    private final Map<String, File> fileMapping = new ConcurrentHashMap<>();

    public enum ToggleResult {
        NOT_FOUND,
        SUCCESS,
        ALREADY_SET
    }

    public AnnouncementManager(AirCore plugin) {
        this.plugin = plugin;
        this.service = new AnnouncementService(plugin);
        setupInitialFiles();
        reload();
    }

    private void setupInitialFiles() {
        File folder = new File(plugin.getDataFolder(), "announcements");
        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("announcements/example.yml", false);
        }
    }

    public void shutdown() {
        service.stopAllTasks();
        service.clearVisuals();
    }

    public void reload() {
        if (!plugin.isEnabled()) return;

        registry.clear();
        fileMapping.clear();
        service.stopAllTasks();
        service.clearVisuals();

        File folder = new File(plugin.getDataFolder(), "announcements");
        if (folder.exists()) {
            loadRecursive(folder);
        }

        registry.forEach((key, sec) -> {
            if (!sec.getBoolean("enabled", true)) return;

            int interval = sec.getInt("interval", 0);
            if (interval > 0) {
                service.scheduleAnnouncement(sec, interval);
            }
        });
    }

    private void loadRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadRecursive(file);
            } else if (file.getName().endsWith(".yml")) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = config.getConfigurationSection("announcements");
                if (root == null) continue;

                for (String key : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec != null) {
                        String lowerKey = key.toLowerCase();
                        registry.put(lowerKey, sec);
                        fileMapping.put(lowerKey, file);
                    }
                }
            }
        }
    }

    public ToggleResult setEnabled(String key, boolean enabled) {
        String lowerKey = key.toLowerCase();
        File file = fileMapping.get(lowerKey);
        if (file == null || !file.exists()) return ToggleResult.NOT_FOUND;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "announcements." + lowerKey + ".enabled";

        boolean hasKey = config.contains(path);
        boolean currentVal = config.getBoolean(path, true);

        if (hasKey && currentVal == enabled) return ToggleResult.ALREADY_SET;
        if (!hasKey && enabled) return ToggleResult.ALREADY_SET;

        config.set(path, enabled);
        try {
            config.save(file);
            reload();
            return ToggleResult.SUCCESS;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save announcement state: " + e.getMessage());
            return ToggleResult.NOT_FOUND;
        }
    }

    public void trigger(String key, String args) {
        ConfigurationSection sec = registry.get(key.toLowerCase());
        if (sec != null) {
            service.broadcast(sec, args);
        }
    }

    public ConfigurationSection getAnnouncement(String key) {
        return registry.get(key.toLowerCase());
    }

    public Map<String, ConfigurationSection> getRegistry() {
        return registry;
    }

    public AnnouncementService service() {
        return service;
    }
}