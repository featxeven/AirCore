package com.ftxeven.aircore.module.kits;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class KitsConfig extends BaseConfig {

    private volatile General general;
    private volatile Cooldowns cooldowns;
    private volatile OneTime oneTime;
    private volatile Defaults defaults;

    public KitsConfig(JavaPlugin plugin) {
        super(plugin, "modules/kits.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        cooldowns = readCooldowns(yaml.getConfigurationSection("cooldowns"));
        oneTime = readOneTime(yaml.getConfigurationSection("one-time"));
        defaults = readDefaults(yaml.getConfigurationSection("defaults"));
    }

    public General general() { return general; }
    public Cooldowns cooldowns() { return cooldowns; }
    public OneTime oneTime() { return oneTime; }
    public Defaults defaults() { return defaults; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getString(sec, "first-join", ""),
                getStringList(sec, "disabled-worlds")
        );
    }

    private Cooldowns readCooldowns(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Cooldowns(getBoolean(sec, "reset-on-logout", false), getBoolean(sec, "reset-on-death", false));
    }

    private OneTime readOneTime(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new OneTime(getBoolean(sec, "reset-on-logout", false), getBoolean(sec, "reset-on-death", false));
    }

    private Defaults readDefaults(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Defaults(
                getInt(sec, "cooldown", 86400),
                getBoolean(sec, "one-time", false),
                getBoolean(sec, "drop-on-full-inventory", false),
                getBoolean(sec, "exact-slots", false),
                getBoolean(sec, "requires-permission", true)
        );
    }

    // Section types

    public record General(String firstJoin, List<String> disabledWorlds) {}

    public record Cooldowns(boolean resetOnLogout, boolean resetOnDeath) {}

    public record OneTime(boolean resetOnLogout, boolean resetOnDeath) {}

    public record Defaults(int cooldown, boolean oneTime, boolean dropOnFullInventory, boolean exactSlots, boolean requiresPermission) {}
}