package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.config.BaseFolderConfig;
import com.ftxeven.aircore.config.YamlMaps;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlaceholdersConfig extends BaseFolderConfig {

    private volatile Map<String, Placeholder> placeholders;

    public PlaceholdersConfig(JavaPlugin plugin) {
        super(plugin, "modules/placeholders");
    }

    @Override
    protected void read(List<Source> sources) {
        placeholders = merge(sources, "placeholders", this::readPlaceholder);
    }

    public Map<String, Placeholder> placeholders() {
        return placeholders;
    }

    // Section readers

    private Placeholder readPlaceholder(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        return new Placeholder(readArgs(sec.getList("args")), integer(sec, "cache", 0), readEntries(sec.getList("entries")));
    }

    private List<PlaceholderArg> readArgs(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<PlaceholderArg> args = new ArrayList<>();
        for (Object item : raw) {
            ConfigurationSection argSec = YamlMaps.toSection(item);
            args.add(new PlaceholderArg(string(argSec, "name", ""), string(argSec, "default", "")));
        }
        return List.copyOf(args);
    }

    private List<PlaceholderEntry> readEntries(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<PlaceholderEntry> entries = new ArrayList<>();
        for (Object item : raw) {
            entries.add(readEntry(YamlMaps.toSection(item)));
        }
        return List.copyOf(entries);
    }

    // output/math/bar/random are mutually exclusive per entry (only one is ever set); the
    // engine that walks these picks whichever is non-null.
    private PlaceholderEntry readEntry(ConfigurationSection sec) {
        return new PlaceholderEntry(
                stringList(sec, "conditions"),
                sec.isSet("output") ? sec.getString("output") : null,
                sec.isSet("math") ? sec.getString("math") : null,
                readBar(sec.getConfigurationSection("bar")),
                readRandomOptions(sec.getList("random")),
                readTransforms(sec.getList("transform"))
        );
    }

    private Bar readBar(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return new Bar(
                string(sec, "current", ""),
                string(sec, "max", ""),
                integer(sec, "segments", 10),
                string(sec, "filled-char", "■"),
                string(sec, "empty-char", "□"),
                string(sec, "filled-color", ""),
                string(sec, "empty-color", "")
        );
    }

    private List<RandomOption> readRandomOptions(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<RandomOption> options = new ArrayList<>();
        for (Object item : raw) {
            ConfigurationSection optSec = YamlMaps.toSection(item);
            options.add(new RandomOption(integer(optSec, "weight", 1), stringList(optSec, "conditions"), string(optSec, "output", "")));
        }
        return List.copyOf(options);
    }

    private List<Transform> readTransforms(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<Transform> transforms = new ArrayList<>();
        for (Object item : raw) {
            Transform transform = readTransform(item);
            if (transform != null) {
                transforms.add(transform);
            }
        }
        return List.copyOf(transforms);
    }

    private Transform readTransform(Object item) {
        if (item instanceof String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "uppercase" -> new Transform.Uppercase();
                case "lowercase" -> new Transform.Lowercase();
                case "capitalize" -> new Transform.Capitalize();
                case "trim" -> new Transform.Trim();
                default -> null;
            };
        }
        if (item instanceof Map<?, ?> map && !map.isEmpty()) {
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            Object value = entry.getValue();
            return switch (key) {
                case "truncate" -> readTruncate(value);
                case "replace" -> readReplace(value);
                case "number-format" -> new Transform.NumberFormat(String.valueOf(value));
                default -> null;
            };
        }
        return null;
    }

    private Transform.Truncate readTruncate(Object value) {
        if (value instanceof Number n) {
            return new Transform.Truncate(n.intValue(), "");
        }
        ConfigurationSection sec = YamlMaps.toSection(value);
        return new Transform.Truncate(integer(sec, "length", 32), string(sec, "suffix", ""));
    }

    private Transform.Replace readReplace(Object value) {
        if (value instanceof List<?> list && list.size() >= 2) {
            return new Transform.Replace(String.valueOf(list.get(0)), String.valueOf(list.get(1)));
        }
        return new Transform.Replace("", "");
    }

    // Section types

    public record PlaceholderArg(String name, String defaultValue) {}

    public record Bar(String current, String max, int segments, String filledChar, String emptyChar, String filledColor, String emptyColor) {}

    public record RandomOption(int weight, List<String> conditions, String output) {}

    public sealed interface Transform permits Transform.Uppercase, Transform.Lowercase, Transform.Capitalize, Transform.Trim, Transform.Truncate, Transform.Replace, Transform.NumberFormat {
        record Uppercase() implements Transform {}
        record Lowercase() implements Transform {}
        record Capitalize() implements Transform {}
        record Trim() implements Transform {}
        record Truncate(int length, String suffix) implements Transform {}
        record Replace(String from, String to) implements Transform {}
        record NumberFormat(String pattern) implements Transform {}
    }

    public record PlaceholderEntry(List<String> conditions, String output, String math, Bar bar, List<RandomOption> random, List<Transform> transform) {}

    public record Placeholder(List<PlaceholderArg> args, int cache, List<PlaceholderEntry> entries) {}
}