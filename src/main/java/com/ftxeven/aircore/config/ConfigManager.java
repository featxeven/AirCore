package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

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

    // General settings
    public boolean notifyUpdates() { return config.getBoolean("notify-updates", true); }
    public String getLocale() { return config.getString("lang", "en_US"); }
    public boolean consoleToPlayerFeedback() {
        return config.getBoolean("console-to-player-feedback", true);
    }
    public List<String> disabledCommands() {
        return config.getStringList("disabled-commands");
    }
    public boolean errorOnExcessArgs() { return config.getBoolean("error-on-excess-args", true); }

    // Time format settings
    public String timeFormatMode() {
        return config.getString("time-format.mode", "DETAILED").toUpperCase();
    }
    public int timeFormatGranularity() {
        return config.getInt("time-format.granularity", 4);
    }

    // Permission groups
    public List<String> permissionGroups(String groupName) {
        String path = "permission-groups." + groupName;
        return config.getStringList(path);
    }

    // Command usages
    public String getUsage(String command, String label) {
        String path = "command-usages." + command + ".usage";
        String usage = config.getString(path);
        if (usage == null) {
            throw new IllegalArgumentException("No usage defined for command: " + command);
        }
        return usage.replace("%label%", label);
    }
    public String getUsage(String command, String variant, String label) {
        String path = "command-usages." + command + ".usage-" + variant;
        String usage = config.getString(path);
        if (usage == null) {
            throw new IllegalArgumentException("No usage defined for command: " + command + " (" + variant + ")");
        }
        return usage.replace("%label%", label);
    }

    // Chat settings
    public double chatCooldown() { return config.getDouble("chat.chat-cooldown", 0.0); }
    public int pmReplyExpireSeconds() {
        return config.getInt("chat.reply-expire-after", 30);
    }
    public boolean pmApplyChatCooldown() {
        return config.getBoolean("chat.apply-chat-cooldown", true);
    }
    public boolean pmApplyUrlFormatting() {
        return config.getBoolean("chat.apply-url-formatting", true);
    }
    public boolean pmApplyDisplayTags() {
        return config.getBoolean("chat.apply-display-tags", true);
    }
    public boolean pmAllowSelfMessage() { return config.getBoolean("chat.allow-self-message", false); }

    // Group formatting
    public boolean groupFormatEnabled() {
        return config.getBoolean("chat.group-format.enabled", false);
    }
    public String getGroupFormat(String group) {
        final String defaultVal = config.getString(
                "chat.group-format.formats._DEFAULT_",
                "<gray>%player%</gray> <dark_gray>></dark_gray> <white>%message%</white>"
        );

        ConfigurationSection formats = config.getConfigurationSection("chat.group-format.formats");
        if (formats == null) {
            return defaultVal;
        }

        if (group == null || group.isBlank()) {
            return formats.getString("_DEFAULT_", defaultVal);
        }

        for (String key : formats.getKeys(false)) {
            if (key.equalsIgnoreCase(group)) {
                String fmt = formats.getString(key);
                if (fmt != null && !fmt.isBlank()) {
                    return fmt;
                }
                break;
            }
        }

        return formats.getString("_DEFAULT_", defaultVal);
    }

    // Mentions
    public boolean mentionsEnabled() {
        return config.getBoolean("chat.chat-mentions.enabled", true);
    }
    public String mentionFormat() {
        return config.getString("chat.chat-mentions.format", "");
    }
    public boolean mentionCaseSensitive() {
        return config.getBoolean("chat.chat-mentions.case-sensitive", true);
    }
    public boolean mentionAllowSelf() { return config.getBoolean("chat.chat-mentions.allow-self", false); }

    // Mentions - Sound
    public String mentionSoundName() { return config.getString("chat.chat-mentions.sound.name", ""); }
    public float mentionSoundVolume() { return (float) config.getDouble("chat.chat-mentions.sound.volume", 1.0); }
    public float mentionSoundPitch() { return (float) config.getDouble("chat.chat-mentions.sound.pitch", 1.0); }

    // Mentions - Title
    public String mentionTitleText() { return config.getString("chat.chat-mentions.title.text", ""); }
    public int mentionTitleFadeIn() { return config.getInt("chat.chat-mentions.title.fade-in", 10); }
    public int mentionTitleStay() { return config.getInt("chat.chat-mentions.title.stay", 40); }
    public int mentionTitleFadeOut() { return config.getInt("chat.chat-mentions.title.fade-out", 10); }

    // Mentions - Subtitle
    public String mentionSubtitleText() { return config.getString("chat.chat-mentions.subtitle.text", ""); }

    // Mentions - Actionbar
    public String mentionActionbarText() {
        return config.getString("chat.chat-mentions.actionbar.text", "");
    }

    // Display tags
    public ConfigurationSection displayTagsSection() {
        return config.getConfigurationSection("chat.display-tags");
    }

    public boolean displayTagEnabled(String tagId) {
        return config.getBoolean("chat.display-tags." + tagId + ".enabled", true);
    }

    // URL formatting
    public boolean urlFormattingEnabled() {
        return config.getBoolean("chat.url-formatting.enabled", true);
    }
    public String urlFormatTemplate() {
        return config.getString(
                "chat.url-formatting.format",
                "<dark_aqua><click:open_url:'%link%'><hover:show_text:'<gray>Click to open link:</gray> <dark_aqua>%link%</dark_aqua>'>%link%</hover></click></dark_aqua>"
        );
    }

    // Economy settings
    public boolean economyAllowDecimals() {
        return config.getBoolean("economy.allow-decimals", true);
    }
    public double economyDefaultBalance() {
        return config.getDouble("economy.default-balance", 0.0);
    }
    public double economyMinBalance() {
        return config.getDouble("economy.min-balance", -1.0);
    }
    public double economyMaxBalance() {
        return config.getDouble("economy.max-balance", -1.0);
    }
    public String economyNumberFormat() {
        return config.getString("economy.number-format", "FORMATTED");
    }
    public List<String> economyFormatShortSuffixes() {
        List<String> list = config.getStringList("economy.format-short-suffix");
        return list.isEmpty() ? List.of("", "k", "M", "B", "T", "Q") : list;
    }
    public boolean economyAllowFormatShortInCommand() {
        return config.getBoolean("economy.allow-format-short-in-command", false);
    }
    public double economyMinPayAmount() {
        return config.getDouble("economy.min-pay-amount", 0);
    }
    public double economyMaxPayAmount() {
        return config.getDouble("economy.max-pay-amount", -1);
    }

    // Teleport settings
    public boolean teleportToCenter() {
        return config.getBoolean("teleport.teleport-to-center", false);
    }
    public int teleportCountdownDuration() {
        return config.getInt("teleport.teleport-countdown.duration", 0);
    }
    public boolean teleportCountdownRepeat() {
        return config.getBoolean("teleport.teleport-countdown.repeat-message", false);
    }
    public boolean cancelTeleportOnMove() {
        return config.getBoolean("teleport.cancel-teleport-when.move", true);
    }
    public boolean cancelTeleportOnDamage() {
        return config.getBoolean("teleport.cancel-teleport-when.damage", true);
    }
    public boolean cancelTeleportOnCommand() {
        return config.getBoolean("teleport.cancel-teleport-when.command", false);
    }
    public boolean cancelTeleportOnInteract() {
        return config.getBoolean("teleport.cancel-teleport-when.interact", false);
    }
    public int teleportImmunitySeconds() {
        return config.getInt("teleport.apply-immunity-after-teleport", 3);
    }
    public int teleportRequestCooldown() {
        return config.getInt("teleport.teleport-request-cooldown", 5);
    }
    public int teleportRequestExpireTime() {
        return config.getInt("teleport.teleport-request-expire-time", 60);
    }
    public boolean retainRequestStateOnLogout() {
        return config.getBoolean("teleport.retain-request-state-on-logout", true);
    }
    public boolean teleportToSpawnOnFirstJoin() {
        return config.getBoolean("teleport.teleport-to-spawn-on-first-join", true);
    }
    public boolean teleportToSpawnOnJoin() {
        return config.getBoolean("teleport.teleport-to-spawn-on-join", false);
    }
    public boolean teleportToSpawnOnDeath() {
        return config.getBoolean("teleport.teleport-to-spawn-on-death", false);
    }

    // Homes settings
    public int homesMaxHomes() {
        return config.getInt("homes.max-homes", 3);
    }
    public List<String> homesDisabledWorlds() {
        return config.getStringList("homes.disabled-worlds");
    }
    public boolean homesAllowNames() {
        return config.getBoolean("homes.allow-home-names", true);
    }
    public String homesNameValidationRegex() {
        return config.getString("homes.name-validation-regex", "^[A-Za-z0-9]+$");
    }
    public int homesMaxNameLength() {
        return config.getInt("homes.max-home-name-length", 16);
    }

    // Kits settings
    public String kitsFirstJoinKit() {
        return config.getString("kits.first-join-kit", "");
    }
    public boolean kitsAutoEquip() {
        return config.getBoolean("kits.auto-equip", false);
    }
    public boolean kitsDropItemsWhenFull() {
        return config.getBoolean("kits.drop-items-when-full", false);
    }

    // Misc section

    // Player messages
    public String motdFirstJoin() {
        return config.getString("player-join-leave.motd-first-join");
    }
    public String motdJoin(String group) {
        String path = "player-join-leave.motd-join." + group;
        if (config.contains(path)) {
            return config.getString(path, "");
        }
        return config.getString("player-join-leave.motd-join._DEFAULT_", "");
    }

    // Broadcast messages
    public String broadcastFirstJoin() {
        return config.getString("player-join-leave.broadcast-first-join");
    }
    public String broadcastJoinFormat(String group) {
        String path = "player-join-leave.broadcast-join." + group;
        if (config.contains(path)) {
            return config.getString(path, "");
        }
        return config.getString("player-join-leave.broadcast-join._DEFAULT_", "");
    }
    public String broadcastLeaveFormat(String group) {
        String path = "player-join-leave.broadcast-leave." + group;
        if (config.contains(path)) {
            return config.getString(path, "");
        }
        return config.getString("player-join-leave.broadcast-leave._DEFAULT_", "");
    }

    // Death messages
    public boolean deathMessagesEnabled() {
        return config.getBoolean("death-messages.enabled", true);
    }
    public List<String> deathMessagesDisabledWorlds() {
        return config.getStringList("death-messages.disabled-worlds");
    }

    // Gameplay limits and cooldowns
    public int blocksMaxBlocks() {
        return config.getInt("max-blocks", 20);
    }
    public int commandCooldown(String commandKey) {
        List<Map<?, ?>> list = config.getMapList("command-cooldowns");
        for (Map<?, ?> entry : list) {
            for (Map.Entry<?, ?> e : entry.entrySet()) {
                String rawKey = e.getKey().toString().toLowerCase();
                boolean isStrict = rawKey.startsWith("*");
                String key = isStrict ? rawKey.substring(1) : rawKey;

                // If strict, require exact match
                if (isStrict && key.equalsIgnoreCase(commandKey)) {
                    return Integer.parseInt(e.getValue().toString());
                }

                // If not strict, allow prefix match
                if (!isStrict && commandKey.startsWith(key)) {
                    return Integer.parseInt(e.getValue().toString());
                }
            }
        }
        return 0;
    }
}