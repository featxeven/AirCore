package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommandConfig {
    private final AirCore plugin;
    private FileConfiguration config;
    private final Map<String, CooldownEntry> cooldownEntries = new HashMap<>();

    public record CooldownEntry(String id, String matchKey, int seconds, boolean strict) {}

    public CommandConfig(AirCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "commands.yml");
        if (!file.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadCooldowns();
    }

    public void reload() { load(); }

    private String s(String p, String d) { return config.getString(p, d); }
    private List<String> sl(String p) { return config.getStringList(p); }

    private void loadCooldowns() {
        cooldownEntries.clear();
        ConfigurationSection section = config.getConfigurationSection("command-cooldowns");
        if (section == null) return;

        var pm = Bukkit.getPluginManager();

        for (String id : section.getKeys(false)) {
            String matchKey = section.getString(id + ".key", id).toLowerCase();
            int seconds = section.getInt(id + ".cooldown", 0);
            boolean strict = section.getBoolean(id + ".strict", false);

            cooldownEntries.put(id, new CooldownEntry(id, matchKey, seconds, strict));

            String permNode = "aircore.bypass.command." + id;
            if (pm.getPermission(permNode) == null) {
                pm.addPermission(new Permission(permNode));
            }
        }
    }

    public CooldownEntry findCooldownEntry(String commandLine) {
        if (cooldownEntries.isEmpty()) return null;
        String input = commandLine.toLowerCase().trim();

        CooldownEntry bestMatch = null;
        for (CooldownEntry entry : cooldownEntries.values()) {
            boolean matches = entry.strict()
                    ? input.equals(entry.matchKey())
                    : (input.equals(entry.matchKey()) || input.startsWith(entry.matchKey() + " "));

            if (matches) {
                if (entry.strict()) return entry;
                if (bestMatch == null || entry.matchKey().length() > bestMatch.matchKey().length()) {
                    bestMatch = entry;
                }
            }
        }
        return bestMatch;
    }

    public List<String> disabledCommands() { return sl("disabled-commands"); }
    public String getSelector(String command, String selectorKey) {
        String specific = config.getString("commands." + command + ".selectors." + selectorKey);
        if (specific != null) return specific;

        return config.getString("global-selectors." + selectorKey, selectorKey);
    }

    public String getUsage(String command, String label) {
        return getUsage(command, null, label);
    }

    public String getUsage(String command, String variant, String label) {
        String key = (variant == null) ? "usage" : "usage-" + variant;
        String path = "commands." + command + "." + key;
        String usage = config.getString(path);

        if (usage == null) throw new IllegalArgumentException("Usage not found for: " + command + " (variant: " + variant + ")");

        String processed = usage.replace("%label%", label);

        if (variant != null && processed.contains("%sublabel%")) {
            String selectorKey = variant.contains("-") ? variant.split("-")[0] : variant;
            String selector = getSelector(command, selectorKey);
            processed = processed.replace("%sublabel%", selector);
        }

        return processed;
    }

    public List<String> getAliases(String command) { return sl("commands." + command + ".aliases"); }
}