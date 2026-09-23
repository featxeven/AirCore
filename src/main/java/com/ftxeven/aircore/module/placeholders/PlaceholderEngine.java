package com.ftxeven.aircore.module.placeholders;

import com.ftxeven.aircore.core.cache.BoundedCache;
import com.ftxeven.aircore.core.condition.Condition;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import com.ftxeven.aircore.core.condition.ConditionParser;
import com.ftxeven.aircore.core.condition.ExprEvaluator;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.Bar;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.Placeholder;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.PlaceholderArg;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.PlaceholderEntry;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.RandomOption;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig.Transform;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.module.WeightedRandom;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;

final class PlaceholderEngine {

    private static final int MAX_CACHED_RESULTS = 4096;
    private static final int MAX_CACHED_EXPRESSIONS = 512;

    private final JavaPlugin plugin;
    private final PlaceholdersConfig config;
    private final ConditionEvaluator conditions;
    private final BoundedCache<String, Optional<Condition.Expr>> mathCache = new BoundedCache<>(MAX_CACHED_EXPRESSIONS);
    private final Map<String, CacheEntry> resultCache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHED_RESULTS || eldest.getValue().expired();
                }
            });

    PlaceholderEngine(JavaPlugin plugin, PlaceholdersConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.conditions = new ConditionEvaluator(plugin.getLogger()::warning);
    }

    String resolve(CommandSender viewer, String rawParams) {
        Map<String, Placeholder> defs = config.placeholders();
        Invocation invocation = match(defs, rawParams);
        if (invocation == null) {
            return null;
        }
        return resolveBound(viewer, invocation.name(), invocation.definition(), invocation.providedArgs());
    }

    String resolveExact(CommandSender viewer, String key, List<String> providedArgs) {
        Placeholder definition = config.placeholders().get(key);
        if (definition == null) {
            return null;
        }
        return resolveBound(viewer, key, definition, providedArgs);
    }

    void invalidateCache() {
        resultCache.clear();
        mathCache.clear();
    }

    private record Invocation(String name, Placeholder definition, List<String> providedArgs) {}

    private Invocation match(Map<String, Placeholder> defs, String rawParams) {
        String bestKey = null;
        for (String key : defs.keySet()) {
            boolean matches = rawParams.equals(key) || rawParams.startsWith(key + "_");
            if (matches && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        if (bestKey == null) {
            return null;
        }
        Placeholder definition = defs.get(bestKey);
        String remainder = rawParams.length() > bestKey.length() ? rawParams.substring(bestKey.length() + 1) : "";
        int argCount = definition.args().size();
        List<String> providedArgs = remainder.isEmpty() || argCount == 0
                ? List.of()
                : List.of(remainder.split("_", argCount));
        return new Invocation(bestKey, definition, providedArgs);
    }

    private Map<String, String> bindArgs(List<PlaceholderArg> declared, List<String> provided) {
        if (declared.isEmpty()) {
            return Map.of();
        }
        Map<String, String> bound = new LinkedHashMap<>();
        for (int i = 0; i < declared.size(); i++) {
            PlaceholderArg arg = declared.get(i);
            String value = i < provided.size() && !provided.get(i).isEmpty() ? provided.get(i) : arg.defaultValue();
            bound.put(arg.name(), value);
        }
        return bound;
    }

    // Resolution + caching

    private String resolveBound(CommandSender viewer, String name, Placeholder definition, List<String> providedArgs) {
        Map<String, String> args = bindArgs(definition.args(), providedArgs);
        int cacheSeconds = definition.cache();
        String cacheKey = cacheSeconds > 0 ? cacheKey(viewer, name, args) : null;

        if (cacheKey != null) {
            CacheEntry cached = resultCache.get(cacheKey);
            if (cached != null && !cached.expired()) {
                return cached.value();
            }
        }

        String value = evaluate(viewer, definition, args);

        if (cacheKey != null) {
            resultCache.put(cacheKey, new CacheEntry(value, System.currentTimeMillis() + cacheSeconds * 1000L));
        }
        return value;
    }

    private String evaluate(CommandSender viewer, Placeholder definition, Map<String, String> args) {
        Function<String, String> resolver = Placeholders.resolver(viewer, Map.of());
        for (PlaceholderEntry entry : definition.entries()) {
            if (!conditions.evaluate(bindList(entry.conditions(), args), resolver)) {
                continue;
            }
            return applyTransforms(resolveValue(viewer, entry, args, resolver), entry.transform());
        }
        return "";
    }

    private String resolveValue(CommandSender viewer, PlaceholderEntry entry, Map<String, String> args, Function<String, String> resolver) {
        if (entry.output() != null) {
            return Placeholders.apply(viewer, bindArgs(entry.output(), args), Map.of());
        }
        if (entry.math() != null) {
            return resolveMath(bindArgs(entry.math(), args), resolver);
        }
        if (entry.bar() != null) {
            return resolveBar(viewer, entry.bar(), args);
        }
        if (!entry.random().isEmpty()) {
            return resolveRandom(viewer, entry.random(), args, resolver);
        }
        return "";
    }

    private String resolveMath(String raw, Function<String, String> resolver) {
        Optional<Condition.Expr> parsed = mathCache.get(raw, this::parseMath);
        return parsed.map(expr -> ExprEvaluator.formatNumber(ExprEvaluator.asNumber(expr, resolver))).orElse("");
    }

    private Optional<Condition.Expr> parseMath(String raw) {
        try {
            return Optional.of(ConditionParser.parseExpression(raw));
        } catch (Exception e) {
            plugin.getLogger().warning("Placeholder math expression '" + raw + "' failed to parse: " + e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveBar(CommandSender viewer, Bar bar, Map<String, String> args) {
        double current = ExprEvaluator.parseNumber(Placeholders.apply(viewer, bindArgs(bar.current(), args), Map.of()));
        double max = ExprEvaluator.parseNumber(Placeholders.apply(viewer, bindArgs(bar.max(), args), Map.of()));
        return BarRenderer.render(bar, Double.isNaN(current) ? 0 : current, Double.isNaN(max) ? 0 : max);
    }

    private String resolveRandom(CommandSender viewer, List<RandomOption> options, Map<String, String> args, Function<String, String> resolver) {
        List<RandomOption> eligible = options.stream()
                .filter(option -> conditions.evaluate(bindList(option.conditions(), args), resolver))
                .toList();
        if (eligible.isEmpty()) {
            return "";
        }
        RandomOption chosen = WeightedRandom.pick(eligible, RandomOption::weight);
        return Placeholders.apply(viewer, bindArgs(chosen.output(), args), Map.of());
    }

    private String applyTransforms(String value, List<Transform> transforms) {
        String result = value;
        for (Transform transform : transforms) {
            result = TransformApplier.apply(result, transform);
        }
        return result;
    }

    private List<String> bindList(List<String> raw, Map<String, String> args) {
        return args.isEmpty() ? raw : raw.stream().map(s -> bindArgs(s, args)).toList();
    }

    private String bindArgs(String raw, Map<String, String> args) {
        if (raw == null || args.isEmpty()) {
            return raw;
        }
        String result = raw;
        for (Map.Entry<String, String> entry : args.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private String cacheKey(CommandSender viewer, String name, Map<String, String> args) {
        String owner = viewer instanceof Player player ? player.getUniqueId().toString() : "$console";
        return owner + ":" + name + ":" + args;
    }

    private record CacheEntry(String value, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}