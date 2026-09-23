package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.config.BaseFolderConfig;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.config.YamlMaps;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VariablesConfig extends BaseFolderConfig {

    private volatile boolean orphanCheck;
    private volatile Map<String, CustomEvent> customEvents;
    private volatile Map<String, VariableDefinition> variables;

    public VariablesConfig(JavaPlugin plugin) {
        super(plugin, "modules/variables");
    }

    @Override
    protected void read(List<Source> sources) {
        orphanCheck = firstBoolean(sources, "orphan-check", true);
        customEvents = merge(sources, "custom-events", this::readCustomEvent);
        variables = merge(sources, "variables", this::readVariable);
    }

    public boolean orphanCheck() { return orphanCheck; }
    public Map<String, CustomEvent> customEvents() { return customEvents; }
    public Map<String, VariableDefinition> variables() { return variables; }

    // Section readers

    private CustomEvent readCustomEvent(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        return new CustomEvent(string(sec, "class", ""), string(sec, "player-method", ""), readCaptures(sec.getList("capture")));
    }

    private List<CaptureField> readCaptures(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<CaptureField> fields = new ArrayList<>();
        for (Object item : raw) {
            ConfigurationSection fieldSec = YamlMaps.toSection(item);
            fields.add(new CaptureField(string(fieldSec, "name", ""), string(fieldSec, "method", "")));
        }
        return List.copyOf(fields);
    }

    private VariableDefinition readVariable(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        return new VariableDefinition(
                enumOr(sec, "scope", VariableScope.class, VariableScope.PLAYER),
                enumOr(sec, "type", VariableType.class, VariableType.STRING),
                sec.isSet("default") ? String.valueOf(sec.get("default")) : "",
                readConstraints(sec),
                readHooks(sec)
        );
    }

    private Constraints readConstraints(ConfigurationSection sec) {
        return new Constraints(stringList(sec, "allowed-values"), nullableDouble(sec, "min"), nullableDouble(sec, "max"), integer(sec, "max-length", -1));
    }

    private Double nullableDouble(ConfigurationSection sec, String path) {
        return sec.isSet(path) ? sec.getDouble(path) : null;
    }

    private Hooks readHooks(ConfigurationSection sec) {
        return new Hooks(
                readHook(sec.getConfigurationSection("on-join")),
                readHook(sec.getConfigurationSection("on-kill")),
                readHook(sec.getConfigurationSection("on-death")),
                readHook(sec.getConfigurationSection("on-quit")),
                readHook(sec.getConfigurationSection("on-startup")),
                readIntervalHook(sec.getConfigurationSection("on-interval")),
                readHook(sec.getConfigurationSection("on-add")),
                readHook(sec.getConfigurationSection("on-subtract")),
                readHook(sec.getConfigurationSection("on-set")),
                readHook(sec.getConfigurationSection("on-reset")),
                readHook(sec.getConfigurationSection("on-toggle")),
                readCustomHook(sec.getConfigurationSection("on-custom"))
        );
    }

    private Hook readHook(ConfigurationSection sec) {
        return sec == null ? null : new Hook(readHookEntries(sec.getList("entries")));
    }

    private IntervalHook readIntervalHook(ConfigurationSection sec) {
        return sec == null ? null : new IntervalHook(integer(sec, "interval", 60), readHookEntries(sec.getList("entries")));
    }

    private CustomHook readCustomHook(ConfigurationSection sec) {
        return sec == null ? null : new CustomHook(string(sec, "event", ""), readHookEntries(sec.getList("entries")));
    }

    private List<VariableHookEntry> readHookEntries(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<VariableHookEntry> entries = new ArrayList<>();
        for (Object item : raw) {
            entries.add(readHookEntry(YamlMaps.toSection(item)));
        }
        return List.copyOf(entries);
    }

    private VariableHookEntry readHookEntry(ConfigurationSection sec) {
        return new VariableHookEntry(
                stringList(sec, "conditions"),
                sec.isSet("add") ? sec.getDouble("add") : null,
                sec.isSet("subtract") ? sec.getDouble("subtract") : null,
                sec.isSet("set") ? sec.getString("set") : null,
                bool(sec, "reset", false),
                bool(sec, "toggle", false),
                readOtherVariableWrite(sec.getConfigurationSection("variable")),
                MessageComponents.read(sec),
                stringOrList(sec, "command"),
                sec.isSet("announcement") ? sec.getString("announcement") : null
        );
    }

    private OtherVariableWrite readOtherVariableWrite(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return new OtherVariableWrite(
                string(sec, "key", ""),
                sec.isSet("set") ? sec.getString("set") : null,
                bool(sec, "reset", false),
                sec.isSet("add") ? sec.getDouble("add") : null,
                sec.isSet("subtract") ? sec.getDouble("subtract") : null,
                bool(sec, "toggle", false)
        );
    }

    // Section types

    public enum VariableScope { GLOBAL, PLAYER }

    public enum VariableType { BOOLEAN, STRING, INTEGER, DOUBLE }

    public record CaptureField(String name, String method) {}

    public record CustomEvent(String eventClass, String playerMethod, List<CaptureField> capture) {}

    public record OtherVariableWrite(String key, @Nullable String set, boolean reset, @Nullable Double add, @Nullable Double subtract, boolean toggle) {}

    public record VariableHookEntry(
            List<String> conditions,
            @Nullable Double add,
            @Nullable Double subtract,
            @Nullable String set,
            boolean reset,
            boolean toggle,
            @Nullable OtherVariableWrite variable,
            MessageComponents.Bundle message,
            List<String> command,
            @Nullable String announcement
    ) {}

    public record Hook(List<VariableHookEntry> entries) {}

    public record IntervalHook(int intervalSeconds, List<VariableHookEntry> entries) {}

    public record CustomHook(String event, List<VariableHookEntry> entries) {}

    public record Constraints(List<String> allowedValues, @Nullable Double min, @Nullable Double max, int maxLength) {}

    public record Hooks(
            @Nullable Hook onJoin,
            @Nullable Hook onKill,
            @Nullable Hook onDeath,
            @Nullable Hook onQuit,
            @Nullable Hook onStartup,
            @Nullable IntervalHook onInterval,
            @Nullable Hook onAdd,
            @Nullable Hook onSubtract,
            @Nullable Hook onSet,
            @Nullable Hook onReset,
            @Nullable Hook onToggle,
            @Nullable CustomHook onCustom
    ) {}

    public record VariableDefinition(VariableScope scope, VariableType type, String defaultValue, Constraints constraints, Hooks hooks) {}
}