package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.core.condition.ExprEvaluator;
import com.ftxeven.aircore.module.variables.VariablesConfig.Constraints;
import com.ftxeven.aircore.module.variables.VariablesConfig.Hook;
import com.ftxeven.aircore.module.variables.VariablesConfig.Hooks;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableDefinition;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableHookEntry;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableScope;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

final class VariableCatalog {

    enum EventHook {
        JOIN(Hooks::onJoin), KILL(Hooks::onKill), DEATH(Hooks::onDeath), QUIT(Hooks::onQuit);

        private final Function<Hooks, Hook> extractor;

        EventHook(Function<Hooks, Hook> extractor) {
            this.extractor = extractor;
        }

        @Nullable Hook of(Hooks hooks) {
            return extractor.apply(hooks);
        }
    }

    record Spec(String key, VariableScope scope, VariableType type, String defaultValue, double defaultNumber,
                Constraints constraints, Hooks hooks) {

        boolean isPlayer() {
            return scope == VariableScope.PLAYER;
        }

        boolean isNumeric() {
            return type == VariableType.INTEGER || type == VariableType.DOUBLE;
        }

        @Nullable Hook mutationHook(VariableWrite write) {
            return switch (write) {
                case VariableWrite.Add ignored -> hooks.onAdd();
                case VariableWrite.Subtract ignored -> hooks.onSubtract();
                case VariableWrite.Set ignored -> hooks.onSet();
                case VariableWrite.Reset ignored -> hooks.onReset();
                case VariableWrite.Toggle ignored -> hooks.onToggle();
            };
        }

        static Spec of(String key, VariableDefinition def) {
            double number = ExprEvaluator.parseNumber(def.defaultValue());
            return new Spec(key, def.scope(), def.type(), def.defaultValue(), Double.isFinite(number) ? number : 0.0,
                    def.constraints(), normalize(def.hooks()));
        }
    }

    // a hook attached to one variable; intervalSeconds is only meaningful for interval bindings
    record Binding(Spec spec, List<VariableHookEntry> entries, int intervalSeconds) {}

    private static final Binding[] NONE = new Binding[0];
    static final VariableCatalog EMPTY = new VariableCatalog(Map.of(), Map.of(), Map.of(), NONE, NONE);

    private final Map<String, Spec> specs;
    private final Map<EventHook, Binding[]> events;
    private final Map<String, Binding[]> custom;
    private final Binding[] startup;
    private final Binding[] intervals;
    private final Set<String> keys;
    private final List<String> numericKeys;

    private VariableCatalog(Map<String, Spec> specs, Map<EventHook, Binding[]> events, Map<String, Binding[]> custom,
                            Binding[] startup, Binding[] intervals) {
        this.specs = specs;
        this.events = events;
        this.custom = custom;
        this.startup = startup;
        this.intervals = intervals;
        this.keys = Set.copyOf(specs.keySet());
        this.numericKeys = specs.values().stream().filter(Spec::isNumeric).map(Spec::key).toList();
    }

    @Nullable Spec spec(String key) {
        return specs.get(key);
    }

    Set<String> keys() {
        return keys;
    }

    List<String> numericKeys() {
        return numericKeys;
    }

    Binding[] event(EventHook type) {
        return events.getOrDefault(type, NONE);
    }

    Binding[] custom(String eventKey) {
        return custom.getOrDefault(eventKey, NONE);
    }

    Binding[] startup() {
        return startup;
    }

    Binding[] intervals() {
        return intervals;
    }

    // Build

    static VariableCatalog build(VariablesConfig config, Consumer<String> warn) {
        Map<String, Spec> specs = new LinkedHashMap<>();
        config.variables().forEach((key, definition) -> specs.put(key, Spec.of(key, definition)));

        Map<EventHook, List<Binding>> events = new EnumMap<>(EventHook.class);
        Map<String, List<Binding>> custom = new HashMap<>();
        List<Binding> startup = new ArrayList<>();
        List<Binding> intervals = new ArrayList<>();

        for (Spec spec : specs.values()) {
            Hooks hooks = spec.hooks();
            for (EventHook type : EventHook.values()) {
                Hook hook = type.of(hooks);
                if (hook != null) {
                    events.computeIfAbsent(type, t -> new ArrayList<>()).add(new Binding(spec, hook.entries(), 0));
                }
            }
            if (hooks.onStartup() != null) {
                startup.add(new Binding(spec, hooks.onStartup().entries(), 0));
            }
            if (hooks.onInterval() != null) {
                intervals.add(new Binding(spec, hooks.onInterval().entries(), Math.max(1, hooks.onInterval().intervalSeconds())));
            }
            if (hooks.onCustom() != null) {
                custom.computeIfAbsent(hooks.onCustom().event(), k -> new ArrayList<>())
                        .add(new Binding(spec, hooks.onCustom().entries(), 0));
            }
            validate(spec, specs.keySet(), config.customEvents().keySet(), warn);
        }

        Map<EventHook, Binding[]> eventArrays = new EnumMap<>(EventHook.class);
        events.forEach((type, list) -> eventArrays.put(type, list.toArray(NONE)));
        Map<String, Binding[]> customArrays = new HashMap<>();
        custom.forEach((event, list) -> customArrays.put(event, list.toArray(NONE)));

        return new VariableCatalog(Map.copyOf(specs), eventArrays, customArrays, startup.toArray(NONE), intervals.toArray(NONE));
    }

    // an empty hook is the same as no hook: drop it so runtime code only ever null-checks
    private static Hooks normalize(Hooks h) {
        return new Hooks(
                nonEmpty(h.onJoin()), nonEmpty(h.onKill()), nonEmpty(h.onDeath()), nonEmpty(h.onQuit()), nonEmpty(h.onStartup()),
                h.onInterval() == null || h.onInterval().entries().isEmpty() ? null : h.onInterval(),
                nonEmpty(h.onAdd()), nonEmpty(h.onSubtract()), nonEmpty(h.onSet()), nonEmpty(h.onReset()), nonEmpty(h.onToggle()),
                h.onCustom() == null || h.onCustom().entries().isEmpty() ? null : h.onCustom());
    }

    private static @Nullable Hook nonEmpty(@Nullable Hook hook) {
        return hook == null || hook.entries().isEmpty() ? null : hook;
    }

    // Validation

    private static void validate(Spec spec, Set<String> known, Set<String> customEvents, Consumer<String> warn) {
        Hooks h = spec.hooks();
        if (h.onCustom() != null && !customEvents.contains(h.onCustom().event())) {
            warn.accept("Variable '" + spec.key() + "' listens to unknown custom event '" + h.onCustom().event() + "'");
        }
        if (h.onStartup() != null && spec.isPlayer()) {
            warn.accept("Variable '" + spec.key() + "' has an on-startup hook but is player-scoped; on-startup only fires for global variables");
        }
        Map<String, List<VariableHookEntry>> named = new LinkedHashMap<>();
        named.put("on-join", entries(h.onJoin()));
        named.put("on-kill", entries(h.onKill()));
        named.put("on-death", entries(h.onDeath()));
        named.put("on-quit", entries(h.onQuit()));
        named.put("on-startup", entries(h.onStartup()));
        named.put("on-interval", h.onInterval() == null ? List.of() : h.onInterval().entries());
        named.put("on-add", entries(h.onAdd()));
        named.put("on-subtract", entries(h.onSubtract()));
        named.put("on-set", entries(h.onSet()));
        named.put("on-reset", entries(h.onReset()));
        named.put("on-toggle", entries(h.onToggle()));
        named.put("on-custom", h.onCustom() == null ? List.of() : h.onCustom().entries());
        named.forEach((hook, list) -> list.forEach(entry -> validateEntry(spec, hook, entry, known, warn)));
    }

    private static List<VariableHookEntry> entries(@Nullable Hook hook) {
        return hook == null ? List.of() : hook.entries();
    }

    private static void validateEntry(Spec spec, String hook, VariableHookEntry entry, Set<String> known, Consumer<String> warn) {
        String where = "Variable '" + spec.key() + "' " + hook + ": ";
        if ((entry.add() != null || entry.subtract() != null) && !spec.isNumeric()) {
            warn.accept(where + "'add'/'subtract' only work on integer/double variables");
        }
        if (entry.toggle() && spec.type() != VariableType.BOOLEAN) {
            warn.accept(where + "'toggle' only works on boolean variables");
        }
        if (entry.variable() != null && !known.contains(entry.variable().key())) {
            warn.accept(where + "references unknown variable '" + entry.variable().key() + "'");
        }
    }
}