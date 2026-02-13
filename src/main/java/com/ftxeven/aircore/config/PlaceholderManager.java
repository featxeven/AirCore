package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.PlaceholderService;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class PlaceholderManager {
    public record PlaceholderEntry(List<PlaceholderTier> tiers) {}
    public record PlaceholderTier(List<String> conditions, String output, String fallback) {}

    private final AirCore plugin;
    private final PlaceholderService service;
    private Map<String, PlaceholderEntry> registry = new HashMap<>();

    public PlaceholderManager(AirCore plugin) {
        this.plugin = plugin;
        this.service = new PlaceholderService();

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
        Map<String, PlaceholderEntry> newRegistry = new HashMap<>();
        File folder = new File(plugin.getDataFolder(), "placeholders");

        if (folder.exists()) {
            load(folder, newRegistry);
        }

        this.registry = newRegistry;
    }

    private void load(File dir, Map<String, PlaceholderEntry> registry) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                load(file, registry);
            } else if (file.getName().endsWith(".yml") && !file.getName().startsWith(".")) {
                parseYaml(file, registry);
            }
        }
    }

    private void parseYaml(File file, Map<String, PlaceholderEntry> registry) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("placeholders");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection pSec = root.getConfigurationSection(key);
            if (pSec == null) continue;

            List<PlaceholderTier> tiers = new ArrayList<>();

            if (pSec.contains("priority")) {
                ConfigurationSection prioSec = pSec.getConfigurationSection("priority");
                if (prioSec != null) {
                    List<String> sortedKeys = new ArrayList<>(prioSec.getKeys(false));
                    sortedKeys.sort(Comparator.comparingInt(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 999; }
                    }));

                    for (String pKey : sortedKeys) {
                        ConfigurationSection tSec = prioSec.getConfigurationSection(pKey);
                        if (tSec != null) tiers.add(parseTier(tSec));
                    }
                }
            } else {
                tiers.add(parseTier(pSec));
            }

            registry.put(key.toLowerCase(), new PlaceholderEntry(tiers));
        }
    }

    private PlaceholderTier parseTier(ConfigurationSection sec) {
        List<String> conds = new ArrayList<>();

        if (sec.isList("conditions")) {
            conds.addAll(sec.getStringList("conditions"));
        } else if (sec.contains("conditions")) {
            String s = sec.getString("conditions");
            if (s != null) conds.add(s);
        }

        return new PlaceholderTier(
                conds,
                sec.getString("output"),
                sec.getString("fallback")
        );
    }

    public String resolve(OfflinePlayer player, String key) {
        PlaceholderEntry entry = registry.get(key.toLowerCase());
        if (entry == null) return null;
        return service.resolve(player, entry);
    }
}