package com.ftxeven.aircore.module.chat;

import com.ftxeven.aircore.config.BaseConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ChatConfig extends BaseConfig {

    private volatile boolean enabled;
    private volatile General general;
    private volatile Channels channels;
    private volatile Format format;
    private volatile PlayerInput playerInput;
    private volatile Mentions mentions;
    private volatile Urls urls;
    private volatile Map<String, DisplayTag> displayTags;
    private volatile Filters filters;
    private volatile Warnings warnings;
    private volatile PrivateMessages privateMessages;

    public ChatConfig(JavaPlugin plugin) {
        super(plugin, "modules/chat.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        enabled = getBoolean(yaml, "enabled", true);
        general = readGeneral(yaml.getConfigurationSection("general"));
        format = readFormat(yaml.getConfigurationSection("format"));
        channels = readChannels(yaml.getConfigurationSection("channels"));
        playerInput = readPlayerInput(yaml.getConfigurationSection("player-input"));
        mentions = readMentions(yaml.getConfigurationSection("mentions"));
        urls = readUrls(yaml.getConfigurationSection("urls"));
        displayTags = readDisplayTags(yaml.getConfigurationSection("display-tags"));

        ConfigurationSection moderation = orEmpty(yaml.getConfigurationSection("moderation"));
        filters = readFilters(moderation.getConfigurationSection("filters"));
        warnings = readWarnings(moderation.getConfigurationSection("warnings"));

        privateMessages = readPrivateMessages(yaml.getConfigurationSection("private-messages"));
    }

    public boolean enabled() { return enabled; }
    public General general() { return general; }
    public Format format() { return format; }
    public Channels channels() { return channels; }
    public PlayerInput playerInput() { return playerInput; }
    public Mentions mentions() { return mentions; }
    public Urls urls() { return urls; }
    public Map<String, DisplayTag> displayTags() { return displayTags; }
    public Filters filters() { return filters; }
    public Warnings warnings() { return warnings; }
    public PrivateMessages privateMessages() { return privateMessages; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getDouble(sec, "cooldown", 3),
                getInt(sec, "radius", -1),
                enumOr(sec, "event-priority", EventPriority.class, EventPriority.HIGH)
        );
    }

    private Format readFormat(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Format(
                getBoolean(sec, "enabled", true),
                readStringMap(sec.getConfigurationSection("templates")),
                readStringMap(sec.getConfigurationSection("groups"))
        );
    }

    private Channels readChannels(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Channels(
                getBoolean(sec, "enabled", false),
                getString(sec, "default", "global"),
                getBoolean(sec, "remember-channel", true),
                readChannelDefinitions(sec.getConfigurationSection("definitions"))
        );
    }

    private Map<String, ChannelDefinition> readChannelDefinitions(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, ChannelDefinition> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection ch = orEmpty(sec.getConfigurationSection(key));
            map.put(key, new ChannelDefinition(
                    getString(ch, "format", ""),
                    optionalInt(ch, "radius", -1),
                    ch.getString("prefix", ""),
                    ch.getString("description", ""),
                    optionalEnum(ch, "visibility", Visibility.class, Visibility.PUBLIC),
                    optionalBoolean(ch, "announce-membership", false))
            );
        }
        return Collections.unmodifiableMap(map);
    }

    private PlayerInput readPlayerInput(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new PlayerInput(
                readInputToggle(sec.getConfigurationSection("signs")),
                readInputToggle(sec.getConfigurationSection("anvil")),
                readInputToggle(sec.getConfigurationSection("books"))
        );
    }

    private InputToggle readInputToggle(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new InputToggle(getBoolean(sec, "enabled", false));
    }

    private Mentions readMentions(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Mentions(
                getBoolean(sec, "enabled", true),
                getString(sec, "symbol", "@"),
                getBoolean(sec, "case-sensitive", false),
                getBoolean(sec, "allow-self-mention", false),
                getBoolean(sec, "match-nicknames", false),
                getString(sec, "format", ""),
                readEveryone(sec.getConfigurationSection("everyone")),
                readHere(sec.getConfigurationSection("here")),
                readMentionComponents(sec.getConfigurationSection("components"))
        );
    }

    private Everyone readEveryone(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Everyone(
                getBoolean(sec, "enabled", true),
                getString(sec, "keyword", "everyone"),
                getString(sec, "format", "")
        );
    }

    private Here readHere(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Here(
                getBoolean(sec, "enabled", true),
                getString(sec, "keyword", "here"),
                getString(sec, "format", "")
        );
    }

    private MentionComponents readMentionComponents(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new MentionComponents(
                readTextComponent(sec.getConfigurationSection("chat")),
                readSoundComponent(sec.getConfigurationSection("sound")),
                readTitleComponent(sec.getConfigurationSection("title")),
                readTextComponent(sec.getConfigurationSection("subtitle")),
                readTextComponent(sec.getConfigurationSection("actionbar")),
                readBossbarComponent(sec.getConfigurationSection("bossbar"))
        );
    }

    private TextComponent readTextComponent(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new TextComponent(getBoolean(sec, "enabled", false), sec.getString("text", ""));
    }

    private SoundComponent readSoundComponent(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new SoundComponent(
                getBoolean(sec, "enabled", false),
                sec.getString("key", ""),
                getDouble(sec, "volume", 1.0),
                getDouble(sec, "pitch", 1.0)
        );
    }

    private TitleComponent readTitleComponent(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new TitleComponent(
                getBoolean(sec, "enabled", false),
                sec.getString("text", ""),
                getInt(sec, "fade-in", 10),
                getInt(sec, "stay", 40),
                getInt(sec, "fade-out", 10)
        );
    }

    private BossbarComponent readBossbarComponent(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new BossbarComponent(
                getBoolean(sec, "enabled", false),
                sec.getString("text", ""),
                getInt(sec, "duration", 5),
                sec.getString("color", "WHITE"),
                sec.getString("overlay", "PROGRESS"),
                getBoolean(sec, "countdown", false)
        );
    }

    private Urls readUrls(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Urls(getBoolean(sec, "enabled", true), getString(sec, "format", ""));
    }

    private Map<String, DisplayTag> readDisplayTags(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, DisplayTag> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection tag = orEmpty(sec.getConfigurationSection(key));
            map.put(key, new DisplayTag(
                    getBoolean(tag, "enabled", true),
                    getStringList(tag, "triggers"),
                    optionalBoolean(tag, "case-sensitive", false),
                    getString(tag, "format", ""),
                    tag.getString("gui", ""),
                    readExpiresAfter(tag, key),
                    readStringOrList(tag, "expired-message")
            ));
        }
        return Collections.unmodifiableMap(map);
    }

    private int readExpiresAfter(ConfigurationSection tag, String key) {
        int seconds = tag.getInt("expires-after", 300);
        if (seconds < 1) {
            plugin.getLogger().warning("'expires-after' for display tag '" + key + "' in " + fileName()
                    + " (display-tags." + key + ") must be at least 1 - using 300");
            return 300;
        }
        return seconds;
    }

    private Filters readFilters(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Filters(
                readCapsFilter(sec.getConfigurationSection("caps")),
                readSpamFilter(sec.getConfigurationSection("spam")),
                readAdvertisingFilter(sec.getConfigurationSection("advertising")),
                readProfanityFilter(sec.getConfigurationSection("profanity")),
                readUnicodeFilter(sec.getConfigurationSection("unicode"))
        );
    }

    private CapsFilter readCapsFilter(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new CapsFilter(
                getBoolean(sec, "enabled", false),
                getInt(sec, "threshold", 70),
                getInt(sec, "min-length", 6),
                enumOr(sec, "action", FilterAction.class, FilterAction.REPLACE)
        );
    }

    private SpamFilter readSpamFilter(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new SpamFilter(
                getBoolean(sec, "enabled", false),
                getInt(sec, "window", 4),
                getInt(sec, "max-messages", 3),
                getBoolean(sec, "similarity-check", true),
                getInt(sec, "similarity-threshold", 80)
        );
    }

    private AdvertisingFilter readAdvertisingFilter(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new AdvertisingFilter(
                getBoolean(sec, "enabled", false),
                getBoolean(sec, "block-ips", true),
                getBoolean(sec, "block-domains", true),
                readPatternList(sec, "patterns"),
                readPatternList(sec, "whitelist")
        );
    }

    private ProfanityFilter readProfanityFilter(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new ProfanityFilter(
                getBoolean(sec, "enabled", false),
                getString(sec, "words-file", "data/profanity.txt"),
                enumOr(sec, "action", FilterAction.class, FilterAction.REPLACE),
                getString(sec, "replacement", "*")
        );
    }

    private UnicodeFilter readUnicodeFilter(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new UnicodeFilter(
                getBoolean(sec, "enabled", false),
                getBoolean(sec, "allow-latin-extended", true),
                enumOr(sec, "action", FilterAction.class, FilterAction.REPLACE)
        );
    }

    private Warnings readWarnings(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Warnings(
                getBoolean(sec, "enabled", false),
                getInt(sec, "reset-after", 600),
                getInt(sec, "max-count", -1),
                getBoolean(sec, "count-replacements", true),
                readWarningWeights(sec.getConfigurationSection("weights")),
                Math.max(0, getInt(sec, "repeat-last-step", 0)),
                readWarningSteps(sec.getConfigurationSection("steps"))
        );
    }

    private Map<String, Integer> readWarningWeights(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            int weight = sec.getInt(key, 1);
            if (weight < 0) {
                plugin.getLogger().warning("Warning weight for '" + key + "' in " + fileName()
                        + " (moderation.warnings.weights) can't be negative - using 0");
                weight = 0;
            }
            weights.put(key.toLowerCase(Locale.ROOT), weight);
        }
        return Collections.unmodifiableMap(weights);
    }

    private Map<Integer, WarningStep> readWarningSteps(ConfigurationSection sec) {
        if (sec == null) return Map.of();
        Map<Integer, WarningStep> steps = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Warning step key '" + key + "' in " + fileName()
                        + " (moderation.warnings.steps) must be a whole number - skipping");
                continue;
            }
            if (threshold <= 0) {
                plugin.getLogger().warning("Warning step '" + key + "' in " + fileName()
                        + " (moderation.warnings.steps) must be greater than 0 - skipping");
                continue;
            }
            ConfigurationSection step = orEmpty(sec.getConfigurationSection(key));
            List<String> message = readStringOrList(step, "message");
            List<String> command = readStringOrList(step, "command");
            List<String> notifyStaff = readStringOrList(step, "notify-staff");
            if (message.isEmpty() && command.isEmpty() && notifyStaff.isEmpty()) {
                plugin.getLogger().warning("Warning step '" + key + "' in " + fileName()
                        + " (moderation.warnings.steps) has nothing to do - skipping");
                continue;
            }
            boolean resetOnTrigger = optionalBoolean(step, "reset-on-trigger", false);
            steps.put(threshold, new WarningStep(message, command, notifyStaff, resetOnTrigger));
        }
        return Collections.unmodifiableMap(steps);
    }

    private PrivateMessages readPrivateMessages(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new PrivateMessages(
                getBoolean(sec, "allow-self-message", false),
                getInt(sec, "reply-expires-after", 60),
                getBoolean(sec, "apply-cooldown", true),
                getBoolean(sec, "apply-url-formatting", true),
                getBoolean(sec, "apply-display-tags", true),
                getBoolean(sec, "apply-filters", false)
        );
    }

    // Section types

    public record General(double cooldown, int radius, EventPriority eventPriority) {}

    public record Format(boolean enabled, Map<String, String> templates, Map<String, String> groups) {}

    public enum Visibility { PUBLIC, PRIVATE, RESTRICTED }

    public record ChannelDefinition(String format, int radius, String prefix, String description,
                                    Visibility visibility, boolean announceMembership) {

        public boolean membersOnly() {
            return visibility != Visibility.PUBLIC;
        }

        public boolean isolatesMembers() {
            return visibility == Visibility.PRIVATE;
        }
    }

    public record Channels(boolean enabled, String defaultChannel, boolean rememberChannel, Map<String, ChannelDefinition> definitions) {

        public List<String> joinableBy(Permissible sender) {
            List<String> keys = new ArrayList<>();
            for (String key : definitions.keySet()) {
                if (Permissions.Access.hasChannel(sender, key)) {
                    keys.add(key);
                }
            }
            return keys;
        }
    }

    public record InputToggle(boolean enabled) {}

    public record PlayerInput(InputToggle signs, InputToggle anvil, InputToggle books) {}

    public record TextComponent(boolean enabled, String text) {}

    public record SoundComponent(boolean enabled, String key, double volume, double pitch) {}

    public record TitleComponent(boolean enabled, String text, int fadeIn, int stay, int fadeOut) {}

    public record BossbarComponent(boolean enabled, String text, int duration, String color, String overlay, boolean countdown) {}

    public record MentionComponents(TextComponent chat, SoundComponent sound, TitleComponent title, TextComponent subtitle, TextComponent actionbar, BossbarComponent bossbar) {}

    public record Everyone(boolean enabled, String keyword, String format) {}

    public record Here(boolean enabled, String keyword, String format) {}

    public record Mentions(boolean enabled, String symbol, boolean caseSensitive, boolean allowSelfMention,
                           boolean matchNicknames, String format, Everyone everyone, Here here, MentionComponents components) {}

    public record Urls(boolean enabled, String format) {}

    public record DisplayTag(boolean enabled, List<String> triggers, boolean caseSensitive, String format,
                             String gui, int expiresAfter, List<String> expiredMessage) {

        public boolean hasGui() {
            return gui != null && !gui.isBlank();
        }
    }

    public enum FilterAction { REPLACE, BLOCK }

    public record CapsFilter(boolean enabled, int threshold, int minLength, FilterAction action) {}

    public record SpamFilter(boolean enabled, int window, int maxMessages, boolean similarityCheck, int similarityThreshold) {}

    public record AdvertisingFilter(boolean enabled, boolean blockIps, boolean blockDomains, List<Pattern> patterns, List<Pattern> whitelist) {}

    public record ProfanityFilter(boolean enabled, String wordsFile, FilterAction action, String replacement) {}

    public record UnicodeFilter(boolean enabled, boolean allowLatinExtended, FilterAction action) {}

    public record WarningStep(List<String> message, List<String> command, List<String> notifyStaff, boolean resetOnTrigger) {}

    public record Warnings(boolean enabled, int resetAfter, int maxCount, boolean countReplacements,
                           Map<String, Integer> weights, int repeatLastStep, Map<Integer, WarningStep> steps) {

        public int weightFor(String filterName) {
            return weights.getOrDefault(filterName, 1);
        }
    }

    public record Filters(CapsFilter caps, SpamFilter spam, AdvertisingFilter advertising, ProfanityFilter profanity, UnicodeFilter unicode) {}

    public record PrivateMessages(boolean allowSelfMessage, int replyExpiresAfter, boolean applyCooldown, boolean applyUrlFormatting, boolean applyDisplayTags, boolean applyFilters) {}
}