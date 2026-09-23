package com.ftxeven.aircore.module.economy.worth;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

public final class WorthItemsConfig extends BaseConfig {

    private volatile Map<String, Double> materials;
    private volatile List<WorthRule> custom;

    public WorthItemsConfig(JavaPlugin plugin) {
        super(plugin, "data/worth/items.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        materials = readMaterials(yaml.getConfigurationSection("materials"));
        custom = readCustom(yaml.getList("custom"));
    }

    public Map<String, Double> materials() { return materials; }

    public List<WorthRule> customRules() { return custom; }

    public OptionalDouble findMaterial(String key) {
        Double value = materials.get(key.toLowerCase(Locale.ROOT));
        return value != null ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    // Section readers

    private Map<String, Double> readMaterials(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, Double> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            if (!(raw instanceof Number number)) {
                plugin.getLogger().warning("Invalid worth '" + raw + "' for '" + key + "' in " + fileName() + " (materials." + key + "), skipping");
                continue;
            }
            double value = number.doubleValue();
            if (value < 0) {
                plugin.getLogger().warning("Worth for '" + key + "' in " + fileName() + " (materials." + key + ") can't be negative, skipping");
                continue;
            }
            map.put(key.toLowerCase(Locale.ROOT), value);
        }
        return Collections.unmodifiableMap(map);
    }

    private List<WorthRule> readCustom(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<WorthRule> parsed = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (!(raw.get(i) instanceof Map<?, ?> map)) {
                plugin.getLogger().warning("Entry #" + (i + 1) + " under 'custom' in " + fileName() + " is not a valid map, skipping");
                continue;
            }
            WorthRule rule = readRule(map, i + 1);
            if (rule != null) {
                parsed.add(rule);
            }
        }
        return List.copyOf(parsed);
    }

    private WorthRule readRule(Map<?, ?> map, int index) {
        Object worthRaw = map.get("worth");
        if (!(worthRaw instanceof Number number)) {
            plugin.getLogger().warning("Custom rule #" + index + " in " + fileName() + " is missing a valid 'worth' value, skipping");
            return null;
        }
        double worth = number.doubleValue();
        if (worth < 0) {
            plugin.getLogger().warning("Custom rule #" + index + " in " + fileName() + " has a negative 'worth', skipping");
            return null;
        }

        MatchRules match = new MatchRules(
                stringList(map, "materials"),
                stringList(map, "names"),
                stringList(map, "lores"),
                stringList(map, "enchantments"),
                stringList(map, "nbt-keys"),
                stringList(map, "custom-model-data"),
                stringList(map, "item-models"),
                stringList(map, "plugin-items")
        );

        if (match.isEmpty()) {
            plugin.getLogger().warning("Custom rule #" + index + " in " + fileName() + " has no match conditions, skipping");
            return null;
        }

        return new WorthRule(match, worth);
    }

    private List<String> stringList(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            values.add(String.valueOf(item));
        }
        return values;
    }
}