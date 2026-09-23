package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LeaderboardsConfig extends BaseConfig {

    private static final String BALANCE = "balance";
    private static final String VARIABLE_PREFIX = "variable:";

    private volatile Map<String, Leaderboard> leaderboards;

    public LeaderboardsConfig(JavaPlugin plugin) {
        super(plugin, "data/leaderboards.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        leaderboards = readLeaderboards(yaml.getConfigurationSection("leaderboards"));
    }

    public Map<String, Leaderboard> leaderboards() {
        return leaderboards;
    }

    public Optional<Leaderboard> leaderboard(String id) {
        return Optional.ofNullable(leaderboards.get(id));
    }

    // Section readers

    private Map<String, Leaderboard> readLeaderboards(@Nullable ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, Leaderboard> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Leaderboard leaderboard = readLeaderboard(sec.getConfigurationSection(key), key);
            if (leaderboard != null) {
                map.put(key, leaderboard);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private @Nullable Leaderboard readLeaderboard(@Nullable ConfigurationSection sec, String id) {
        sec = orEmpty(sec);

        String rawSource = sec.getString("source", "").trim();
        if (rawSource.isEmpty()) {
            plugin.getLogger().warning("Leaderboard '" + id + "' in " + fileName() + " has no 'source' value, skipping");
            return null;
        }
        Source source = readSource(rawSource);
        if (source == null) {
            plugin.getLogger().warning("Invalid source '" + rawSource + "' for leaderboard '" + id + "' in " + fileName()
                    + ", skipping");
            return null;
        }

        int refreshInterval = getInt(sec, "refresh-interval", 60);
        if (refreshInterval < 1) {
            plugin.getLogger().warning("refresh-interval must be at least 1 for leaderboard '" + id + "' in " + fileName() + ", using 1");
            refreshInterval = 1;
        }
        int maxEntries = getInt(sec, "max-entries", 2000);

        return new Leaderboard(
                id,
                source,
                enumOr(sec, "order", Order.class, Order.DESC),
                maxEntries > 0 ? maxEntries : -1,
                refreshInterval,
                getDouble(sec, "min-value", 0)
        );
    }

    private static @Nullable Source readSource(String raw) {
        if (raw.equalsIgnoreCase(BALANCE)) {
            return new Source.Balance();
        }
        if (raw.regionMatches(true, 0, VARIABLE_PREFIX, 0, VARIABLE_PREFIX.length())) {
            String variable = raw.substring(VARIABLE_PREFIX.length()).trim();
            if (!variable.isEmpty()) {
                return new Source.Variable(variable);
            }
        }
        return null;
    }

    // Section types

    public enum Order { ASC, DESC }

    public sealed interface Source {
        record Balance() implements Source {}

        record Variable(String id) implements Source {}
    }

    // maxEntries is -1 when unlimited
    public record Leaderboard(String id, Source source, Order order, int maxEntries, int refreshInterval, double minValue) {

        public boolean unlimited() {
            return maxEntries < 0;
        }
    }
}