package com.ftxeven.aircore.module.economy.worth;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WorthModifiersConfig extends BaseConfig {

    private volatile Map<String, Double> enchantments;
    private volatile Durability durability;
    private volatile Potions potions;

    public WorthModifiersConfig(JavaPlugin plugin) {
        super(plugin, "data/worth/modifiers.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        enchantments = readModifierMap(yaml.getConfigurationSection("enchantments"));
        durability = readDurability(yaml.getConfigurationSection("durability"));
        potions = readPotions(yaml.getConfigurationSection("potions"));
    }

    public Map<String, Double> enchantments() { return enchantments; }
    public Durability durability() { return durability; }
    public Potions potions() { return potions; }

    public double enchantmentModifier(String key) {
        return enchantments.getOrDefault(key.toLowerCase(Locale.ROOT), 0.0);
    }

    // Section readers

    private Map<String, Double> readModifierMap(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, Double> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            if (raw instanceof Number number) {
                map.put(key.toLowerCase(Locale.ROOT), number.doubleValue());
            } else {
                plugin.getLogger().warning("Invalid modifier '" + raw + "' for '" + key + "' in " + fileName() + ", skipping");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private Durability readDurability(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Durability(Math.clamp(getDouble(sec, "min-worth-percent", 0.10), 0, 1));
    }

    private Potions readPotions(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Potions(
                getBoolean(sec, "enabled", true),
                getDouble(sec, "upgraded-multiplier", 0.5),
                getDouble(sec, "extended-multiplier", 0.25),
                readModifierMap(sec.getConfigurationSection("effects"))
        );
    }

    // Section types

    public record Durability(double minWorthPercent) {}

    public record Potions(boolean enabled, double upgradedMultiplier, double extendedMultiplier, Map<String, Double> effects) {}
}