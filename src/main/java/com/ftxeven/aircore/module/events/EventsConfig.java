package com.ftxeven.aircore.module.events;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class EventsConfig extends BaseConfig {

    private volatile boolean enabled;
    private volatile Join join;
    private volatile Leave leave;
    private volatile Death death;

    public EventsConfig(JavaPlugin plugin) {
        super(plugin, "modules/events.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        enabled = getBoolean(yaml, "enabled", true);
        join = readJoin(yaml.getConfigurationSection("join"));
        leave = readLeave(yaml.getConfigurationSection("leave"));
        death = readDeath(yaml.getConfigurationSection("death"));
    }

    public boolean enabled() { return enabled; }
    public Join join() { return join; }
    public Leave leave() { return leave; }
    public Death death() { return death; }

    // Section readers

    private Join readJoin(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Join(
                getBoolean(sec, "enabled", true),
                readMotd(sec.getConfigurationSection("motd")),
                readJoinBroadcast(sec.getConfigurationSection("broadcast"))
        );
    }

    private Motd readMotd(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Motd(getStringList(sec, "first-join"), readStringListMap(sec.getConfigurationSection("returning")));
    }

    private JoinBroadcast readJoinBroadcast(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new JoinBroadcast(getString(sec, "first-join", ""), readStringMap(sec.getConfigurationSection("returning")));
    }

    private Leave readLeave(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Leave(getBoolean(sec, "enabled", true), readStringMap(sec.getConfigurationSection("broadcast")));
    }

    private Death readDeath(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Death(getBoolean(sec, "enabled", true), getStringList(sec, "disabled-worlds"));
    }

    // Section types

    public record Motd(List<String> firstJoin, Map<String, List<String>> returning) {}

    public record JoinBroadcast(String firstJoin, Map<String, String> returning) {}

    public record Join(boolean enabled, Motd motd, JoinBroadcast broadcast) {}

    public record Leave(boolean enabled, Map<String, String> broadcast) {}

    public record Death(boolean enabled, List<String> disabledWorlds) {}
}