package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.AirCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class PlaceholderLoader {

    public record PlaceholderEntry(List<PlaceholderTier> tiers) {}
    public record PlaceholderTier(List<String> conditions, String output, String fallback) {}

    private final AirCore plugin;

    public PlaceholderLoader(AirCore plugin) {
        this.plugin = plugin;
    }

    public Map<String, PlaceholderEntry> loadAll() {
        Map<String, PlaceholderEntry> registry = new HashMap<>();
        File folder = new File(plugin.getDataFolder(), "placeholders");

        if (!folder.exists()) return registry;

        loadRecursively(folder, registry);
        return registry;
    }

    private void loadRecursively(File dir, Map<String, PlaceholderEntry> registry) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadRecursively(file, registry);
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
                    sortedKeys.sort(Comparator.comparingInt(Integer::parseInt));

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
}