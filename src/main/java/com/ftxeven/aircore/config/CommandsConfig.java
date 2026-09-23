package com.ftxeven.aircore.config;

import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.core.command.DurationUnits.DurationUnit;
import com.ftxeven.aircore.core.command.DynamicCommand;
import com.ftxeven.aircore.core.command.Shortcuts.Shortcut;
import com.ftxeven.aircore.core.command.tabcomplete.TabPosition;
import com.ftxeven.aircore.core.command.tabcomplete.TabPosition.CopyPosition;
import com.ftxeven.aircore.core.command.tabcomplete.TabPosition.EntriesPosition;
import com.ftxeven.aircore.core.command.tabcomplete.TabPosition.TabEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;

public final class CommandsConfig extends BaseConfig {

    private volatile Map<String, Shortcut> shortcuts;
    private volatile Map<String, String> selectors;
    private volatile Map<String, DurationUnit> durationUnits;
    private volatile Map<String, CommandCooldown> cooldowns;
    private volatile Map<String, Bundle> bundles;

    public CommandsConfig(JavaPlugin plugin) {
        super(plugin, "commands.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        shortcuts = readShortcuts(yaml.getConfigurationSection("shortcuts"));
        selectors = readSelectors(yaml.getConfigurationSection("selectors"));
        durationUnits = readDurationUnits(yaml.getConfigurationSection("duration-units"));
        cooldowns = readCooldowns(yaml.getConfigurationSection("cooldowns"));
        bundles = readBundles(yaml.getConfigurationSection("commands"));
    }

    public Map<String, Shortcut> shortcuts() { return shortcuts; }
    public Map<String, String> selectors() { return selectors; }
    public Map<String, DurationUnit> durationUnits() { return durationUnits; }
    public Map<String, CommandCooldown> cooldowns() { return cooldowns; }
    public Map<String, Bundle> bundles() { return bundles; }

    public Optional<DynamicCommand> findCommand(String id) {
        for (Bundle bundle : bundles.values()) {
            if (bundle.enabled() && bundle.commands().containsKey(id)) {
                return Optional.of(bundle.commands().get(id));
            }
        }
        return Optional.empty();
    }

    /** the command config for id, or a disabled placeholder if it's missing or its bundle is disabled */
    public DynamicCommand findCommandOrDisabled(String id) {
        return findCommand(id).orElseGet(() -> DynamicCommand.disabled(id));
    }

    // Section readers

    private Map<String, Shortcut> readShortcuts(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, Shortcut> shortcuts = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Shortcut shortcut = readShortcut(sec.getConfigurationSection(key), key);
            if (shortcut != null) shortcuts.put(key, shortcut);
        }
        return Collections.unmodifiableMap(shortcuts);
    }

    private Shortcut readShortcut(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        String runs = sec.getString("runs", "");
        if (runs.isBlank()) {
            plugin.getLogger().warning("Shortcut '" + key + "' in " + fileName() + " has no 'runs' value, skipping");
            return null;
        }
        return new Shortcut(runs, optionalStringList(sec, "aliases"));
    }

    private Map<String, String> readSelectors(ConfigurationSection sec) {
        sec = orEmpty(sec);
        Map<String, String> selectors = new LinkedHashMap<>();
        selectors.put(Selectors.ALL, getString(sec, Selectors.ALL, "@a"));
        selectors.put(Selectors.SERVER, getString(sec, Selectors.SERVER, "@server"));
        for (String key : sec.getKeys(false)) {
            selectors.putIfAbsent(key.toLowerCase(Locale.ROOT), sec.getString(key));
        }
        return Collections.unmodifiableMap(selectors);
    }

    private Map<String, DurationUnit> readDurationUnits(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, DurationUnit> units = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection unitSec = orEmpty(sec.getConfigurationSection(key));
            int ticks = getInt(unitSec, "ticks", 1);
            if (ticks < 1) {
                plugin.getLogger().warning("ticks must be at least 1 for duration unit '" + key + "' in " + fileName() + ", using 1");
                ticks = 1;
            }
            units.put(key, new DurationUnit(ticks));
        }
        return Collections.unmodifiableMap(units);
    }

    private Map<String, CommandCooldown> readCooldowns(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, CommandCooldown> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection cd = orEmpty(sec.getConfigurationSection(key));
            int seconds = getInt(cd, "seconds", 0);
            if (seconds <= 0) {
                plugin.getLogger().warning("Cooldown '" + key + "' in " + fileName() + " (cooldowns." + key + ") needs 'seconds' greater than 0, skipping");
                continue;
            }
            map.put(key.toLowerCase(Locale.ROOT), new CommandCooldown(
                    seconds,
                    optionalBoolean(cd, "strict", false),
                    cd.isSet("message") ? cd.getString("message") : null,
                    optionalBoolean(cd, "per-arg", false)
            ));
        }
        return Collections.unmodifiableMap(map);
    }

    private Map<String, Bundle> readBundles(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, Bundle> bundles = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            bundles.put(key, readBundle(sec.getConfigurationSection(key), key));
        }
        return Collections.unmodifiableMap(bundles);
    }

    private Bundle readBundle(ConfigurationSection sec, String id) {
        sec = orEmpty(sec);
        boolean enabled = getBoolean(sec, "enabled", true);

        if (!sec.isSet("bundle")) {
            return new Bundle(enabled, Map.of(id, readCommand(sec, id, id)));
        }
        if (!sec.isConfigurationSection("bundle")) {
            plugin.getLogger().warning("'bundle' for '" + id + "' in " + fileName() + " is not a valid map, skipping");
            return new Bundle(enabled, Map.of());
        }
        return new Bundle(enabled, readBundleCommands(sec.getConfigurationSection("bundle"), id));
    }

    private Map<String, DynamicCommand> readBundleCommands(ConfigurationSection sec, String bundleId) {
        if (sec == null) return Map.of();
        Map<String, DynamicCommand> commands = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            commands.put(key, readCommand(sec.getConfigurationSection(key), bundleId, key));
        }
        return Collections.unmodifiableMap(commands);
    }

    private DynamicCommand readCommand(ConfigurationSection sec, String bundleId, String cmdId) {
        sec = orEmpty(sec);
        return new DynamicCommand(
                getBoolean(sec, "enabled", true),
                getString(sec, "name", cmdId),
                optionalStringList(sec, "aliases"),
                getString(sec, "usage", ""),
                sec.getString("usage-others", ""),
                readLabelMap(sec.getConfigurationSection("actions")),
                readGui(sec, bundleId, cmdId),
                readTabComplete(sec, bundleId, cmdId)
        );
    }

    private Map<String, String> readGui(ConfigurationSection cmdSec, String bundleId, String cmdId) {
        if (!cmdSec.isSet("gui")) {
            return Map.of();
        }
        if (!cmdSec.isConfigurationSection("gui")) {
            plugin.getLogger().warning("'gui' for '" + cmdId + "' in bundle '" + bundleId + "' in " + fileName()
                    + " is not a valid map, skipping");
            return Map.of();
        }
        return readLabelMap(cmdSec.getConfigurationSection("gui"));
    }

    // Tab-complete

    private Map<Integer, TabPosition> readTabComplete(ConfigurationSection cmdSec, String bundleId, String cmdId) {
        ConfigurationSection tabSec = cmdSec.getConfigurationSection("tab-complete");
        boolean wrapped = tabSec != null;
        if (!wrapped) {
            tabSec = cmdSec;
        }

        Map<Integer, TabPosition> positions = new LinkedHashMap<>();
        for (String key : tabSec.getKeys(false)) {
            int position;
            try {
                position = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                if (wrapped) {
                    plugin.getLogger().warning("Tab-complete key '" + key + "' for '" + cmdId + "' in bundle '" + bundleId + "' in " + fileName() + " is not a valid position number, skipping");
                }
                continue;
            }
            TabPosition parsed = readPosition(tabSec, key, bundleId + "." + cmdId);
            if (parsed != null) {
                positions.put(position, parsed);
            }
        }
        validateCopyPositions(positions, bundleId + "." + cmdId);
        return Collections.unmodifiableMap(positions);
    }

    private void validateCopyPositions(Map<Integer, TabPosition> positions, String context) {
        for (Map.Entry<Integer, TabPosition> entry : positions.entrySet()) {
            if (!(entry.getValue() instanceof CopyPosition copy)) continue;
            if (copy.copyArg() == entry.getKey()) {
                plugin.getLogger().warning("Tab-complete position " + entry.getKey() + " for '" + context + "' in " + fileName() + " has copy-arg pointing to itself, ignoring");
                entry.setValue(new EntriesPosition(List.of()));
            } else if (positions.get(copy.copyArg()) instanceof CopyPosition) {
                plugin.getLogger().warning("Tab-complete position " + entry.getKey() + " for '" + context + "' in " + fileName() + " has copy-arg pointing to another copy-arg position, ignoring");
                entry.setValue(new EntriesPosition(List.of()));
            }
        }
    }

    private TabPosition readPosition(ConfigurationSection tabSec, String key, String context) {
        if (tabSec.isConfigurationSection(key)) {
            ConfigurationSection entrySec = tabSec.getConfigurationSection(key);
            if (entrySec.isSet("copy-arg")) {
                return readCopyPosition(entrySec, key, context);
            }
            return new EntriesPosition(readEntries(entrySec, context, key));
        }
        if (tabSec.isList(key)) {
            return new EntriesPosition(readEntries(tabSec.getList(key), context, key));
        }
        plugin.getLogger().warning("Tab-complete position '" + key + "' for '" + context + "' in " + fileName() + " is not a valid map or list, skipping");
        return null;
    }

    private TabPosition readCopyPosition(ConfigurationSection sec, String key, String context) {
        int copyArg = sec.getInt("copy-arg", -1);
        if (copyArg <= 0) {
            plugin.getLogger().warning("Invalid copy-arg at tab-complete position '" + key + "' for '" + context + "' in " + fileName() + ", skipping");
            return null;
        }
        return new CopyPosition(copyArg, sec.getStringList("exclude-sources"), readEntries(sec.get("append-sources"), context, key));
    }

    private List<TabEntry> readEntries(Object raw, String context, String position) {
        if (raw instanceof ConfigurationSection cs) {
            TabEntry entry = readEntry(cs, context, position);
            return entry != null ? List.of(entry) : List.of();
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) return List.of();
            if (list.get(0) instanceof Map || list.get(0) instanceof ConfigurationSection) {
                List<TabEntry> entries = new ArrayList<>();
                for (Object item : list) {
                    TabEntry entry = readEntry(item, context, position);
                    if (entry != null) entries.add(entry);
                }
                return List.copyOf(entries);
            }
            return List.of(new TabEntry(readSources(list), "", List.of(), false));
        }
        return List.of();
    }

    private TabEntry readEntry(Object raw, String context, String position) {
        Function<String, Object> field = fieldAccessor(raw);
        if (field == null) {
            plugin.getLogger().warning("Tab-complete entry at position '" + position + "' for '" + context + "' in " + fileName() + " is not a valid map, skipping");
            return null;
        }
        List<String> sources = readSources(field.apply("sources"));
        if (sources.isEmpty()) {
            plugin.getLogger().warning("Tab-complete entry at position '" + position + "' for '" + context + "' in " + fileName() + " has no sources");
        }
        return new TabEntry(sources, asString(field.apply("requires"), ""), toStringList(field.apply("conditions")), Boolean.TRUE.equals(field.apply("suffix-mode")));
    }

    private static Function<String, Object> fieldAccessor(Object raw) {
        if (raw instanceof ConfigurationSection cs) return cs::get;
        if (raw instanceof Map<?, ?> map) return map::get;
        return null;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) values.add(String.valueOf(item));
        return List.copyOf(values);
    }

    private static List<String> readSources(Object raw) {
        return mergeAngleBracketGroups(toStringList(raw));
    }

    private static List<String> mergeAngleBracketGroups(List<String> raw) {
        List<String> merged = new ArrayList<>();
        StringBuilder open = null;
        for (String token : raw) {
            if (open != null) {
                open.append(", ").append(token);
                if (token.indexOf('>') >= 0) {
                    merged.add(open.toString());
                    open = null;
                }
                continue;
            }
            if (token.indexOf('<') >= 0 && token.indexOf('>') < 0) {
                open = new StringBuilder(token);
            } else {
                merged.add(token);
            }
        }
        if (open != null) merged.add(open.toString());
        return merged;
    }

    private static String asString(Object raw, String fallback) {
        return raw != null ? String.valueOf(raw) : fallback;
    }

    // Section types

    public record CommandCooldown(int seconds, boolean strict, String message, boolean perArg) {}

    public record Bundle(boolean enabled, Map<String, DynamicCommand> commands) {}
}