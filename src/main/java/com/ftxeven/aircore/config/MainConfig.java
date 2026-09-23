package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MainConfig extends BaseConfig {

    private volatile General general;
    private volatile Formatting formatting;
    private volatile ToggleDefaults toggleDefaults;
    private volatile WorldRestrictions worldRestrictions;

    public MainConfig(JavaPlugin plugin) {
        super(plugin, "config.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        formatting = readFormatting(yaml.getConfigurationSection("formatting"));
        toggleDefaults = readToggleDefaults(yaml.getConfigurationSection("toggle-defaults"));
        worldRestrictions = readWorldRestrictions(yaml.getConfigurationSection("world-restrictions"));
    }

    public General general() { return general; }
    public Formatting formatting() { return formatting; }
    public ToggleDefaults toggleDefaults() { return toggleDefaults; }
    public WorldRestrictions worldRestrictions() { return worldRestrictions; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getString(sec, "lang", "en_US"),
                getString(sec, "items-lang", "en_US"),
                getBoolean(sec, "notify-updates", true),
                getBoolean(sec, "console-feedback", true),
                getBoolean(sec, "strict-args", true),
                getBoolean(sec, "allow-nickname-lookup", true)
        );
    }

    private Formatting readFormatting(ConfigurationSection sec) {
        sec = orEmpty(sec);
        DurationStyle duration = new DurationStyle(
                enumOr(sec, "duration.mode", DurationMode.class, DurationMode.CUSTOM),
                Math.max(1, getInt(sec, "duration.granularity", 2))
        );
        ZoneId zone = zone(getString(sec, "timezone", "system"));
        return new Formatting(
                enumOr(sec, "message-format", MessageFormat.class, MessageFormat.SMART),
                duration,
                pattern(getString(sec, "time", "HH:mm"), "HH:mm").withZone(zone),
                pattern(getString(sec, "date", "dd/MM/yy"), "dd/MM/yy").withZone(zone),
                zone
        );
    }

    private ToggleDefaults readToggleDefaults(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new ToggleDefaults(
                getBoolean(sec, "msgtoggle", true),
                getBoolean(sec, "socialspy", false),
                getBoolean(sec, "chattoggle", true),
                getBoolean(sec, "mentiontoggle", true),
                getBoolean(sec, "announcetoggle", true),
                getBoolean(sec, "paytoggle", true),
                getBoolean(sec, "payconfirmtoggle", true),
                getBoolean(sec, "tptoggle", true),
                getBoolean(sec, "tpautoaccept", false),
                getBoolean(sec, "tpconfirmtoggle", true)
        );
    }

    private WorldRestrictions readWorldRestrictions(ConfigurationSection sec) {
        if (sec == null) {
            return new WorldRestrictions(Map.of());
        }
        Map<String, Set<RestrictedFeature>> byWorld = new LinkedHashMap<>();
        for (String world : sec.getKeys(false)) {
            Set<RestrictedFeature> features = enumSet(sec, world, RestrictedFeature.class);
            if (!features.isEmpty()) {
                byWorld.put(world, features);
            }
        }
        return new WorldRestrictions(Collections.unmodifiableMap(byWorld));
    }

    private DateTimeFormatter pattern(String raw, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid pattern '" + raw + "' in " + fileName() + ", using '" + fallback + "'");
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    private ZoneId zone(String raw) {
        if (raw == null || raw.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("Invalid timezone '" + raw + "' in " + fileName() + ", using system default");
            return ZoneId.systemDefault();
        }
    }

    // Section types

    public enum MessageFormat { MINI, LEGACY, SMART }

    public enum DurationMode { DETAILED, SEQUENTIAL, CUSTOM }

    public enum RestrictedFeature { GOD, FLY, SPEED, CUSTOM_TIME, CUSTOM_WEATHER, AFK }

    public record General(String lang, String itemsLang, boolean notifyUpdates, boolean consoleFeedback,
                          boolean strictArgs, boolean allowNicknameLookup) {}

    public record DurationStyle(DurationMode mode, int granularity) {}

    public record Formatting(MessageFormat messageFormat, DurationStyle duration, DateTimeFormatter time, DateTimeFormatter date, ZoneId timezone) {}

    public record ToggleDefaults(
            boolean msgToggle, boolean socialSpy, boolean chatToggle, boolean mentionToggle, boolean announceToggle,
            boolean payToggle, boolean payConfirmToggle, boolean tpToggle, boolean tpAutoAccept, boolean tpConfirmToggle
    ) {}

    public record WorldRestrictions(Map<String, Set<RestrictedFeature>> byWorld) {

        public boolean isRestricted(String world, RestrictedFeature feature) {
            Set<RestrictedFeature> features = byWorld.get(world);
            return features != null && features.contains(feature);
        }
    }
}