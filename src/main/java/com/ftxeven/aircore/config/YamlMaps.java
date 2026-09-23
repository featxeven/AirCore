package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Map;

public final class YamlMaps {

    private YamlMaps() {}

    public static ConfigurationSection toSection(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            return section;
        }
        MemoryConfiguration section = new MemoryConfiguration();
        if (raw instanceof Map<?, ?> map) {
            populate(section, map);
        }
        return section;
    }

    private static void populate(ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                populate(section.createSection(key), nested);
            } else {
                section.set(key, value);
            }
        }
    }
}