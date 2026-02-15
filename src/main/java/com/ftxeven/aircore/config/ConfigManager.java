package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

public final class ConfigManager {
    private final AirCore plugin;
    private FileConfiguration config;

    public ConfigManager(AirCore plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    private String s(String p, String d) { return config.getString(p, d); }
    private boolean b(String p, boolean d) { return config.getBoolean(p, d); }
    private int i(String p, int d) { return config.getInt(p, d); }
    private double d(String p, double d) { return config.getDouble(p, d); }
    private List<String> sl(String p) { return config.getStringList(p); }

    private String getGroupedValue(String path, String group) {
        String specific = path + "." + group;
        return config.contains(specific) ? config.getString(specific) : config.getString(path + "._DEFAULT_", "");
    }

    // General & Chat
    public boolean notifyUpdates() { return b("notify-updates", true); }
    public String getLocale() { return s("lang", "en_US"); }
    public boolean consoleToPlayerFeedback() { return b("console-to-player-feedback", true); }
    public List<String> disabledCommands() { return sl("disabled-commands"); }
    public boolean errorOnExcessArgs() { return b("error-on-excess-args", true); }
    public String timeFormatMode() { return s("time-format.mode", "DETAILED").toUpperCase(); }
    public int timeFormatGranularity() { return i("time-format.granularity", 4); }

    public double chatCooldown() { return d("chat.chat-cooldown", 0.0); }
    public int pmReplyExpireSeconds() { return i("chat.reply-expire-after", 30); }
    public boolean pmApplyChatCooldown() { return b("chat.apply-chat-cooldown", true); }
    public boolean pmApplyUrlFormatting() { return b("chat.apply-url-formatting", true); }
    public boolean pmApplyDisplayTags() { return b("chat.apply-display-tags", true); }
    public boolean pmAllowSelfMessage() { return b("chat.allow-self-message", false); }

    // Commands & Permissions
    public List<String> permissionGroups(String group) { return sl("permission-groups." + group); }

    public String getUsage(String cmd, String label) { return getUsage(cmd, null, label); }
    public String getUsage(String cmd, String variant, String label) {
        String path = "command-usages." + cmd + (variant == null ? ".usage" : ".usage-" + variant);
        String usage = config.getString(path);
        if (usage == null) throw new IllegalArgumentException("No usage: " + cmd + (variant == null ? "" : " " + variant));
        return usage.replace("%label%", label);
    }

    // Group Formatting
    public boolean groupFormatEnabled() { return b("chat.group-format.enabled", false); }
    public String getGroupFormat(String group) {
        String def = s("chat.group-format.formats._DEFAULT_", "<gray>%player%</gray> > <white>%message%</white>");
        ConfigurationSection sec = config.getConfigurationSection("chat.group-format.formats");
        if (sec == null || group == null || group.isBlank()) return def;

        return sec.getKeys(false).stream()
                .filter(k -> k.equalsIgnoreCase(group))
                .findFirst()
                .map(sec::getString)
                .filter(f -> !f.isBlank())
                .orElse(sec.getString("_DEFAULT_", def));
    }

    // Mentions
    public boolean mentionsEnabled() { return b("chat.chat-mentions.enabled", true); }
    public String mentionFormat() { return s("chat.chat-mentions.format", ""); }
    public boolean mentionCaseSensitive() { return b("chat.chat-mentions.case-sensitive", true); }
    public boolean mentionAllowSelf() { return b("chat.chat-mentions.allow-self", false); }
    public String mentionSoundName() { return s("chat.chat-mentions.sound.name", ""); }
    public float mentionSoundVolume() { return (float) d("chat.chat-mentions.sound.volume", 1.0); }
    public float mentionSoundPitch() { return (float) d("chat.chat-mentions.sound.pitch", 1.0); }
    public String mentionTitleText() { return s("chat.chat-mentions.title.text", ""); }
    public int mentionTitleFadeIn() { return i("chat.chat-mentions.title.fade-in", 10); }
    public int mentionTitleStay() { return i("chat.chat-mentions.title.stay", 40); }
    public int mentionTitleFadeOut() { return i("chat.chat-mentions.title.fade-out", 10); }
    public String mentionSubtitleText() { return s("chat.chat-mentions.subtitle.text", ""); }
    public String mentionActionbarText() { return s("chat.chat-mentions.actionbar.text", ""); }

    // Economy & Teleport
    public ConfigurationSection displayTagsSection() { return config.getConfigurationSection("chat.display-tags"); }
    public boolean displayTagEnabled(String id) { return b("chat.display-tags." + id + ".enabled", true); }
    public boolean urlFormattingEnabled() { return b("chat.url-formatting.enabled", true); }
    public String urlFormatTemplate() { return s("chat.url-formatting.format", "<dark_aqua><click:open_url:'%link%'><hover:show_text:'<gray>Click to open link:</gray> <dark_aqua>%link%</dark_aqua>'>%link%</hover></click></dark_aqua>"); }

    public boolean economyAllowDecimals() { return b("economy.allow-decimals", true); }
    public double economyDefaultBalance() { return d("economy.default-balance", 0.0); }
    public double economyMinBalance() { return d("economy.min-balance", -1.0); }
    public double economyMaxBalance() { return d("economy.max-balance", -1.0); }
    public String economyNumberFormat() { return s("economy.number-format", "FORMATTED"); }
    public List<String> economyFormatShortSuffixes() {
        List<String> list = sl("economy.format-short-suffix");
        return list.isEmpty() ? List.of("", "k", "M", "B", "T", "Q") : list;
    }
    public boolean economyAllowFormatShortInCommand() { return b("economy.allow-format-short-in-command", false); }
    public double economyMinPayAmount() { return d("economy.min-pay-amount", 0); }
    public double economyMaxPayAmount() { return d("economy.max-pay-amount", -1); }

    public boolean teleportToCenter() { return b("teleport.teleport-to-center", false); }
    public int teleportCountdownDuration() { return i("teleport.teleport-countdown.duration", 0); }
    public boolean teleportCountdownRepeat() { return b("teleport.teleport-countdown.repeat-message", false); }
    public boolean cancelTeleportOnMove() { return b("teleport.cancel-teleport-when.move", true); }
    public boolean cancelTeleportOnDamage() { return b("teleport.cancel-teleport-when.damage", true); }
    public boolean cancelTeleportOnCommand() { return b("teleport.cancel-teleport-when.command", false); }
    public boolean cancelTeleportOnInteract() { return b("teleport.cancel-teleport-when.interact", false); }
    public int teleportImmunitySeconds() { return i("teleport.apply-immunity-after-teleport", 3); }
    public int teleportRequestCooldown() { return i("teleport.teleport-request-cooldown", 5); }
    public int teleportRequestExpireTime() { return i("teleport.teleport-request-expire-time", 60); }
    public boolean retainRequestStateOnLogout() { return b("teleport.retain-request-state-on-logout", true); }
    public boolean teleportToSpawnOnFirstJoin() { return b("teleport.teleport-to-spawn-on-first-join", true); }
    public boolean teleportToSpawnOnJoin() { return b("teleport.teleport-to-spawn-on-join", false); }
    public boolean teleportToSpawnOnDeath() { return b("teleport.teleport-to-spawn-on-death", false); }

    // Homes & Kits
    public int homesMaxHomes() { return i("homes.max-homes", 3); }
    public List<String> homesDisabledWorlds() { return sl("homes.disabled-worlds"); }
    public boolean homesAllowNames() { return b("homes.allow-home-names", true); }
    public String homesNameValidationRegex() { return s("homes.name-validation-regex", "^[A-Za-z0-9]+$"); }
    public int homesMaxNameLength() { return i("homes.max-home-name-length", 16); }
    public String kitsFirstJoinKit() { return s("kits.first-join-kit", ""); }
    public boolean kitsAutoEquip() { return b("kits.auto-equip", false); }
    public boolean kitsDropItemsWhenFull() { return b("kits.drop-items-when-full", false); }

    // Join/Leave & Death
    public String motdFirstJoin() { return s("player-join-leave.motd-first-join", null); }
    public String motdJoin(String g) { return getGroupedValue("player-join-leave.motd-join", g); }
    public String broadcastFirstJoin() { return s("player-join-leave.broadcast-first-join", null); }
    public String broadcastJoinFormat(String g) { return getGroupedValue("player-join-leave.broadcast-join", g); }
    public String broadcastLeaveFormat(String g) { return getGroupedValue("player-join-leave.broadcast-leave", g); }
    public boolean deathMessagesEnabled() { return b("death-messages.enabled", true); }
    public List<String> deathMessagesDisabledWorlds() { return sl("death-messages.disabled-worlds"); }

    // Gameplay Limits
    public int blocksMaxBlocks() { return i("max-blocks", 20); }
    public int commandCooldown(String cmd) {
        for (Map<?, ?> entry : config.getMapList("command-cooldowns")) {
            for (Map.Entry<?, ?> e : entry.entrySet()) {
                String raw = e.getKey().toString().toLowerCase();
                boolean strict = raw.startsWith("*");
                String key = strict ? raw.substring(1) : raw;
                if ((strict && key.equalsIgnoreCase(cmd)) || (!strict && cmd.startsWith(key)))
                    return Integer.parseInt(e.getValue().toString());
            }
        }
        return 0;
    }
}