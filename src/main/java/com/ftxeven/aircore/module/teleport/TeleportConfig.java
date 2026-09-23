package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.config.BaseConfig;
import com.ftxeven.aircore.model.TeleportType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeleportConfig extends BaseConfig {

    private volatile General general;
    private volatile Countdown countdown;
    private volatile PostTeleport postTeleport;
    private volatile Requests requests;
    private volatile SpawnSettings spawn;
    private volatile Warps warps;
    private volatile Respawn respawn;
    private volatile Back back;

    public TeleportConfig(JavaPlugin plugin) {
        super(plugin, "modules/teleport.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        countdown = readCountdown(yaml.getConfigurationSection("countdown"));
        postTeleport = readPostTeleport(yaml.getConfigurationSection("post-teleport"));
        requests = readRequests(yaml.getConfigurationSection("requests"));
        spawn = readSpawn(yaml.getConfigurationSection("spawn"));
        warps = readWarps(yaml.getConfigurationSection("warps"));
        respawn = readRespawn(yaml.getConfigurationSection("respawn"));
        back = readBack(yaml.getConfigurationSection("back"));
    }

    public General general() { return general; }
    public Countdown countdown() { return countdown; }
    public PostTeleport postTeleport() { return postTeleport; }
    public Requests requests() { return requests; }
    public SpawnSettings spawn() { return spawn; }
    public Warps warps() { return warps; }
    public Respawn respawn() { return respawn; }
    public Back back() { return back; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(getBoolean(sec, "teleport-to-center", true), getBoolean(sec, "safe-landing", false),
                Math.max(0, getInt(sec, "safe-landing-radius", 8)), getStringList(sec, "disabled-worlds"), readRestrictionExemptTypes(sec));
    }

    private List<String> readRestrictionExemptTypes(ConfigurationSection sec) {
        return getStringList(sec, "restriction-exempt-types").stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }

    private Countdown readCountdown(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Countdown(
                getInt(sec, "duration", 5),
                getBoolean(sec, "repeat-message", true),
                readCancelOn(sec.getConfigurationSection("cancel-on")),
                readOverrides(sec.getList("overrides"))
        );
    }

    private CancelOn readCancelOn(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new CancelOn(getBoolean(sec, "move", true), getDouble(sec, "move-threshold", 0.15), getBoolean(sec, "damage", true), getBoolean(sec, "command", false), getBoolean(sec, "interact", false));
    }

    private List<CountdownOverride> readOverrides(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<CountdownOverride> parsed = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (!(raw.get(i) instanceof Map<?, ?> map)) {
                plugin.getLogger().warning("Entry #" + (i + 1) + " under 'countdown.overrides' in " + fileName() + " is not a valid map, skipping");
                continue;
            }
            CountdownOverride override = readOverride(map, i + 1);
            if (override != null) {
                parsed.add(override);
            }
        }
        return List.copyOf(parsed);
    }

    private CountdownOverride readOverride(Map<?, ?> map, int index) {
        Object durationRaw = map.get("duration");
        if (!(durationRaw instanceof Number number)) {
            plugin.getLogger().warning("Countdown override #" + index + " in " + fileName() + " is missing a valid 'duration' value, skipping");
            return null;
        }
        int duration = number.intValue();
        if (duration < 0) {
            plugin.getLogger().warning("Countdown override #" + index + " in " + fileName() + " has a negative 'duration', skipping");
            return null;
        }
        return new CountdownOverride(stringList(map, "worlds"), upperStringList(map, "types"), duration);
    }

    private List<String> stringList(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            values.add(String.valueOf(item));
        }
        return values;
    }

    private List<String> upperStringList(Map<?, ?> map, String key) {
        return stringList(map, key).stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }

    private PostTeleport readPostTeleport(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new PostTeleport(getInt(sec, "immunity-duration", 3));
    }

    private Requests readRequests(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Requests(
                getBoolean(sec, "require-confirmation", true),
                getInt(sec, "confirmation-timeout", 60),
                getInt(sec, "cooldown", 5),
                getInt(sec, "expire-after", 60),
                getBoolean(sec, "retain-on-logout", false),
                getInt(sec, "max-pending", 5)
        );
    }

    private SpawnSettings readSpawn(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new SpawnSettings(getBoolean(sec, "on-join", false), getBoolean(sec, "on-death", true));
    }

    private Warps readWarps(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Warps(getStringList(sec, "disabled-worlds"));
    }

    private Respawn readRespawn(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Respawn(getBoolean(sec, "prefer-bed", false), getBoolean(sec, "prefer-anchor", true));
    }

    private Back readBack(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Back(getInt(sec, "max-history", 3), getStringList(sec, "disabled-worlds"),
                readSaveOn(sec.getConfigurationSection("save-on")), getBoolean(sec, "notify-on-death", true));
    }

    private SaveOn readSaveOn(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new SaveOn(getBoolean(sec, "death", true), getBoolean(sec, "teleport", true));
    }

    // Section types

    public record General(boolean teleportToCenter, boolean safeLanding, int safeLandingRadius, List<String> disabledWorlds, List<String> restrictionExemptTypes) {

        public boolean isRestrictionExempt(TeleportType type) {
            return restrictionExemptTypes.contains(type.name());
        }
    }

    public record CancelOn(boolean move, double moveThreshold, boolean damage, boolean command, boolean interact) {}

    public record CountdownOverride(List<String> worlds, List<String> types, int duration) {

        public boolean matches(String world, TeleportType type) {
            boolean worldMatches = worlds.isEmpty() || worlds.contains(world);
            boolean typeMatches = types.isEmpty() || types.contains(type.name());
            return worldMatches && typeMatches;
        }
    }

    public record Countdown(int duration, boolean repeatMessage, CancelOn cancelOn, List<CountdownOverride> overrides) {

        public int resolveDuration(String world, TeleportType type) {
            for (CountdownOverride override : overrides) {
                if (override.matches(world, type)) {
                    return override.duration();
                }
            }
            return duration;
        }
    }

    public record PostTeleport(int immunityDuration) {}

    public record Requests(boolean requireConfirmation, int confirmationTimeout, int cooldown, int expireAfter, boolean retainOnLogout, int maxPending) {}

    public record SpawnSettings(boolean onJoin, boolean onDeath) {}

    public record Warps(List<String> disabledWorlds) {}

    public record Respawn(boolean preferBed, boolean preferAnchor) {}

    public record SaveOn(boolean death, boolean teleport) {}

    public record Back(int maxHistory, List<String> disabledWorlds, SaveOn saveOn, boolean notifyOnDeath) {}
}