package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class BaseConfig implements LoadableConfig {

    protected final JavaPlugin plugin;
    private final String fileName;

    protected BaseConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public final Runnable prepare() {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException e) {
            throw new RuntimeException("I/O error: " + e.getMessage(), e);
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException("Malformed YAML: " + e.getMessage(), e);
        }

        return () -> read(yaml);
    }

    @Override
    public final String fileName() {
        return fileName;
    }

    protected abstract void read(ConfigurationSection yaml);

    // Parsing helpers

    protected static ConfigurationSection orEmpty(ConfigurationSection section) {
        return section != null ? section : new YamlConfiguration();
    }

    protected static Map<String, String> readLabelMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.getString(key, key));
        }
        return Collections.unmodifiableMap(map);
    }

    protected Map<String, String> readStringMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.getString(key, ""));
        }
        return Collections.unmodifiableMap(map);
    }

    protected Map<String, List<String>> readStringListMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, readStringOrList(section, key));
        }
        return Collections.unmodifiableMap(map);
    }

    protected List<String> readStringOrList(ConfigurationSection section, String path) {
        if (section == null || !section.isSet(path)) {
            return List.of();
        }
        if (section.isList(path)) {
            return List.copyOf(section.getStringList(path));
        }
        if (section.isString(path)) {
            return List.of(section.getString(path, ""));
        }
        return List.of();
    }

    protected String getString(ConfigurationSection section, String path, String fallback) {
        warnIfMissing(section, path, fallback);
        return section.getString(path, fallback);
    }

    protected int getInt(ConfigurationSection section, String path, int fallback) {
        if (warnIfMissing(section, path, fallback)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected int optionalInt(ConfigurationSection section, String path, int fallback) {
        if (!section.isSet(path)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected double getDouble(ConfigurationSection section, String path, double fallback) {
        if (warnIfMissing(section, path, fallback)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected boolean getBoolean(ConfigurationSection section, String path, boolean fallback) {
        if (warnIfMissing(section, path, fallback)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected boolean optionalBoolean(ConfigurationSection section, String path, boolean fallback) {
        if (!section.isSet(path)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected List<String> getStringList(ConfigurationSection section, String path) {
        return getStringList(section, path, List.of());
    }

    protected List<String> getStringList(ConfigurationSection section, String path, List<String> fallback) {
        if (warnIfMissing(section, path, fallback)) {
            return fallback;
        }
        Object raw = section.get(path);
        if (raw instanceof List<?>) {
            return section.getStringList(path);
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
        return fallback;
    }

    protected List<String> optionalStringList(ConfigurationSection section, String path) {
        if (!section.isSet(path)) {
            return List.of();
        }
        Object raw = section.get(path);
        if (raw instanceof List<?>) {
            return section.getStringList(path);
        }
        plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", skipping");
        return List.of();
    }

    protected <T extends Enum<T>> T enumOr(ConfigurationSection section, String path, Class<T> type, T fallback) {
        if (warnIfMissing(section, path, fallback)) {
            return fallback;
        }
        String raw = section.getString(path, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
            return fallback;
        }
    }

    protected <T extends Enum<T>> T optionalEnum(ConfigurationSection section, String path, Class<T> type, T fallback) {
        if (!section.isSet(path)) {
            return fallback;
        }
        String raw = section.getString(path, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", using " + fallback);
            return fallback;
        }
    }

    protected <T extends Enum<T>> Set<T> enumSet(ConfigurationSection section, String path, Class<T> type) {
        Set<T> values = EnumSet.noneOf(type);
        for (String raw : getStringList(section, path)) {
            try {
                values.add(Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid value '" + raw + "' at '" + path + "' in " + fileName + ", skipping");
            }
        }
        return values;
    }

    protected Pattern regexOr(ConfigurationSection section, String path, String fallback) {
        String raw = getString(section, path, fallback);
        try {
            return Pattern.compile(raw);
        } catch (PatternSyntaxException e) {
            plugin.getLogger().warning("Invalid regex '" + raw + "' at '" + path + "' in " + fileName + ", using '" + fallback + "'");
            return Pattern.compile(fallback);
        }
    }

    protected List<Pattern> readPatternList(ConfigurationSection section, String path) {
        List<Pattern> patterns = new ArrayList<>();
        for (String raw : getStringList(section, path)) {
            try {
                patterns.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                patterns.add(Pattern.compile(Pattern.quote(raw), Pattern.CASE_INSENSITIVE));
            }
        }
        return List.copyOf(patterns);
    }

    protected record NameValidation(int maxLength, Pattern validationRegex, List<Pattern> blacklist, boolean extendProfanityWords) {}

    protected NameValidation readNameValidation(ConfigurationSection section) {
        return new NameValidation(
                getInt(section, "max-length", 16),
                regexOr(section, "validation-regex", "^[a-zA-Z0-9_]+$"),
                readPatternList(section, "blacklist"),
                getBoolean(section, "extend-profanity-words", false)
        );
    }

    private boolean warnIfMissing(ConfigurationSection section, String path, Object fallback) {
        if (section.isSet(path)) {
            return false;
        }
        plugin.getLogger().warning("Missing '" + path + "' in " + fileName + ", using default: " + fallback);
        return true;
    }
}