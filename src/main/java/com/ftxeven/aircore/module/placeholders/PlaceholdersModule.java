package com.ftxeven.aircore.module.placeholders;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public final class PlaceholdersModule {

    private final PlaceholdersConfig config;
    private final PlaceholderEngine engine;

    public PlaceholdersModule(JavaPlugin plugin, PlaceholdersConfig config) {
        this.config = config;
        this.engine = new PlaceholderEngine(plugin, config);
    }

    public String resolve(CommandSender viewer, String rawParams) {
        return engine.resolve(viewer, rawParams);
    }

    public String parse(CommandSender viewer, String key, List<String> args) {
        return engine.resolveExact(viewer, key, args);
    }

    public boolean has(String key) {
        return config.placeholders().containsKey(key);
    }

    public Set<String> keys() {
        return config.placeholders().keySet();
    }

    public void invalidateCache() {
        engine.invalidateCache();
    }
}