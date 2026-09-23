package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class BaseFolderConfig implements LoadableConfig {

    private static final String EXTENSION = ".yml";

    protected final JavaPlugin plugin;
    private final BundledDefaults bundledDefaults;
    private final String folder;
    private Boolean sharedFreshness;

    protected BaseFolderConfig(JavaPlugin plugin, String folder) {
        this.plugin = plugin;
        this.bundledDefaults = new BundledDefaults(plugin);
        this.folder = folder;
    }

    public final void useSharedFreshness(boolean fresh) {
        this.sharedFreshness = fresh;
    }

    @Override
    public final Runnable prepare() {
        File dir = new File(plugin.getDataFolder(), folder);
        boolean fresh = sharedFreshness != null ? sharedFreshness : bundledDefaults.isFresh(folder, EXTENSION, Set.of());
        bundledDefaults.extract(fresh, folder + "/", EXTENSION, Set.of(), BundledDefaults.ALWAYS_PROTECTED);
        List<Source> sources = loadFiles(dir);
        return () -> read(sources);
    }

    @Override
    public final String fileName() {
        return folder;
    }

    protected abstract void read(List<Source> sources);

    public record Source(String path, ConfigurationSection root) {

        public boolean enabled() {
            return root.getBoolean("enabled", true);
        }

        public ConfigurationSection section(String key) {
            return root.getConfigurationSection(key);
        }
    }

    public record Merged<T>(Map<String, T> values, Set<String> disabledByFile) {}

    private List<Source> loadFiles(File dir) {
        if (!dir.isDirectory()) {
            return List.of();
        }

        List<File> files = new ArrayList<>();
        collect(dir, files);
        files.sort(Comparator.comparing(File::getPath));

        Path base = dir.toPath();
        List<Source> sources = new ArrayList<>(files.size());
        List<String> failed = new ArrayList<>();
        for (File file : files) {
            String relative = folder + "/" + base.relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                sources.add(new Source(relative, yaml));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("Failed to load " + relative + ": " + e.getMessage());
                failed.add(relative);
            }
        }

        if (!failed.isEmpty()) {
            throw new RuntimeException("Malformed YAML in " + failed.size() + " file(s): " + String.join(", ", failed));
        }

        return sources;
    }

    private void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                out.add(child);
            }
        }
    }

    protected <T> Map<String, T> merge(List<Source> sources, String sectionKey, BiFunction<ConfigurationSection, String, T> reader) {
        Map<String, T> merged = new LinkedHashMap<>();
        for (Source source : sources) {
            if (!source.enabled()) {
                continue;
            }
            ConfigurationSection section = source.section(sectionKey);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                if (merged.containsKey(key)) {
                    plugin.getLogger().warning("Duplicate " + sectionKey + " entry '" + key + "' in " + source.path() + ", overriding the previous definition");
                }
                merged.put(key, reader.apply(section.getConfigurationSection(key), key));
            }
        }
        return merged;
    }

    protected <T> Merged<T> mergeTracked(List<Source> sources, String sectionKey, BiFunction<ConfigurationSection, String, T> reader) {
        Map<String, T> merged = merge(sources, sectionKey, reader);

        Set<String> disabledByFile = new LinkedHashSet<>();
        for (Source source : sources) {
            if (source.enabled()) {
                continue;
            }
            ConfigurationSection section = source.section(sectionKey);
            if (section != null) {
                disabledByFile.addAll(section.getKeys(false));
            }
        }
        disabledByFile.removeAll(merged.keySet());

        return new Merged<>(merged, Set.copyOf(disabledByFile));
    }

    protected boolean firstBoolean(List<Source> sources, String path, boolean fallback) {
        for (Source source : sources) {
            if (source.enabled() && source.root().isSet(path)) {
                return source.root().getBoolean(path, fallback);
            }
        }
        return fallback;
    }

    protected static <T extends Enum<T>> T enumOr(ConfigurationSection section, String path, Class<T> type, T fallback) {
        String raw = section.getString(path, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // Parsing helpers

    protected static ConfigurationSection orEmpty(ConfigurationSection section) {
        return section != null ? section : new YamlConfiguration();
    }

    protected static String string(ConfigurationSection section, String path, String fallback) {
        return section.getString(path, fallback);
    }

    protected static int integer(ConfigurationSection section, String path, int fallback) {
        return section.isInt(path) || section.isLong(path) ? section.getInt(path) : fallback;
    }

    protected static double decimal(ConfigurationSection section, String path, double fallback) {
        return section.isDouble(path) || section.isInt(path) ? section.getDouble(path) : fallback;
    }

    protected static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section.isBoolean(path) ? section.getBoolean(path) : fallback;
    }

    protected static List<String> stringList(ConfigurationSection section, String path) {
        return section.isList(path) ? List.copyOf(section.getStringList(path)) : List.of();
    }

    protected static List<String> stringOrList(ConfigurationSection section, String path) {
        if (section.isList(path)) {
            return List.copyOf(section.getStringList(path));
        }
        if (section.isString(path)) {
            return List.of(section.getString(path, ""));
        }
        return List.of();
    }
}