package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MainConfig extends BaseConfig {

    private static final int MAX_NEAR_DECIMALS = 6;

    // any visible text, spaces included; control characters and the legacy section sign are rejected
    private static final String ITEM_TEXT_REGEX = "^[^\\p{Cntrl}§]+$";

    private volatile General general;
    private volatile Formatting formatting;
    private volatile ToggleDefaults toggleDefaults;
    private volatile UtilityDefaults utilityDefaults;
    private volatile WorldRestrictions worldRestrictions;

    public MainConfig(JavaPlugin plugin) {
        super(plugin, "config.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        formatting = readFormatting(yaml.getConfigurationSection("formatting"));
        toggleDefaults = readToggleDefaults(yaml.getConfigurationSection("toggle-defaults"));
        utilityDefaults = readUtilityDefaults(yaml.getConfigurationSection("utility-defaults"));
        worldRestrictions = readWorldRestrictions(yaml.getConfigurationSection("world-restrictions"));
    }

    public General general() { return general; }
    public Formatting formatting() { return formatting; }
    public ToggleDefaults toggleDefaults() { return toggleDefaults; }
    public UtilityDefaults utilityDefaults() { return utilityDefaults; }
    public WorldRestrictions worldRestrictions() { return worldRestrictions; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getString(sec, "lang", "en_US"),
                getString(sec, "items-lang", "en_US"),
                getBoolean(sec, "notify-updates", true),
                getBoolean(sec, "console-feedback", true),
                getBoolean(sec, "strict-args", true),
                getBoolean(sec, "allow-nickname-lookup", true)
        );
    }

    private Formatting readFormatting(ConfigurationSection sec) {
        sec = orEmpty(sec);
        DurationStyle duration = new DurationStyle(
                enumOr(sec, "duration.mode", DurationMode.class, DurationMode.CUSTOM),
                Math.max(1, getInt(sec, "duration.granularity", 2))
        );
        ZoneId zone = zone(getString(sec, "timezone", "system"));
        return new Formatting(
                enumOr(sec, "message-format", MessageFormat.class, MessageFormat.SMART),
                duration,
                pattern(getString(sec, "time", "HH:mm"), "HH:mm").withZone(zone),
                pattern(getString(sec, "date", "dd/MM/yy"), "dd/MM/yy").withZone(zone),
                zone
        );
    }

    private ToggleDefaults readToggleDefaults(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new ToggleDefaults(
                getBoolean(sec, "msgtoggle", true),
                getBoolean(sec, "socialspy", false),
                getBoolean(sec, "chattoggle", true),
                getBoolean(sec, "mentiontoggle", true),
                getBoolean(sec, "announcetoggle", true),
                getBoolean(sec, "paytoggle", true),
                getBoolean(sec, "payconfirmtoggle", true),
                getBoolean(sec, "tptoggle", true),
                getBoolean(sec, "tpautoaccept", false),
                getBoolean(sec, "tpconfirmtoggle", true)
        );
    }

    private UtilityDefaults readUtilityDefaults(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new UtilityDefaults(
                readBlocking(sec.getConfigurationSection("blocking")),
                readNear(sec.getConfigurationSection("near")),
                readItemText(sec.getConfigurationSection("item-name"), 50),
                readItemLore(sec.getConfigurationSection("item-lore"))
        );
    }

    private Blocking readBlocking(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Blocking(
                getInt(sec, "max-blocked", 20),
                readBlockActions(sec.getConfigurationSection("actions"))
        );
    }

    private BlockActions readBlockActions(ConfigurationSection sec) {
        sec = orEmpty(sec);
        Set<BlockAction> enabled = EnumSet.noneOf(BlockAction.class);
        for (BlockAction action : BlockAction.values()) {
            if (getBoolean(sec, action.key(), action.enabledByDefault())) {
                enabled.add(action);
            }
        }
        return new BlockActions(Collections.unmodifiableSet(enabled));
    }

    private Near readNear(ConfigurationSection sec) {
        sec = orEmpty(sec);
        String where = "'utility-defaults.near' in " + fileName();

        int minRadius = getInt(sec, "min-radius", 1);
        if (minRadius < 1) {
            plugin.getLogger().warning("'min-radius' at " + where + " must be at least 1, using 1");
            minRadius = 1;
        }

        int hardMaxRadius = getInt(sec, "hard-max-radius", 60_000_000);
        if (hardMaxRadius < minRadius) {
            plugin.getLogger().warning("'hard-max-radius' at " + where + " is below 'min-radius', using " + minRadius);
            hardMaxRadius = minRadius;
        }

        int maxRadius = getInt(sec, "max-radius", 500);
        if (maxRadius >= 0 && maxRadius < minRadius) {
            plugin.getLogger().warning("'max-radius' at " + where + " is below 'min-radius', using " + minRadius);
            maxRadius = minRadius;
        }
        if (maxRadius > hardMaxRadius) {
            plugin.getLogger().warning("'max-radius' at " + where + " is above 'hard-max-radius', using " + hardMaxRadius);
            maxRadius = hardMaxRadius;
        }

        int requestedDefault = getInt(sec, "default-radius", 100);
        int ceiling = maxRadius >= 0 ? maxRadius : hardMaxRadius;
        int defaultRadius = Math.min(ceiling, Math.max(minRadius, requestedDefault));
        if (defaultRadius != requestedDefault) {
            plugin.getLogger().warning("'default-radius' at " + where + " is outside the allowed radius range, using " + defaultRadius);
        }

        int maxResults = getInt(sec, "max-results", 15);
        if (maxResults == 0) {
            plugin.getLogger().warning("'max-results' at " + where + " can't be 0, using 1 (use -1 for unlimited)");
            maxResults = 1;
        }

        int decimals = getInt(sec, "distance-decimals", 0);
        if (decimals < 0 || decimals > MAX_NEAR_DECIMALS) {
            int clamped = Math.clamp(decimals, 0, MAX_NEAR_DECIMALS);
            plugin.getLogger().warning("'distance-decimals' at " + where + " must be between 0 and " + MAX_NEAR_DECIMALS + ", using " + clamped);
            decimals = clamped;
        }

        return new Near(
                defaultRadius,
                minRadius,
                maxRadius,
                hardMaxRadius,
                maxResults,
                enumOr(sec, "sort", NearSort.class, NearSort.DISTANCE),
                getBoolean(sec, "ignore-height", false),
                decimals,
                readNearHide(sec.getConfigurationSection("hide"))
        );
    }

    private NearHide readNearHide(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new NearHide(
                getBoolean(sec, "vanished", true),
                getBoolean(sec, "spectators", true),
                getBoolean(sec, "afk", false)
        );
    }

    private ItemText readItemText(ConfigurationSection sec, int defaultMaxLength) {
        sec = orEmpty(sec);
        NameValidation validation = readNameValidation(sec, defaultMaxLength, ITEM_TEXT_REGEX);
        return new ItemText(
                validation.maxLength(),
                validation.validationRegex(),
                validation.blacklist(),
                validation.extendProfanityWords(),
                getBoolean(sec, "italic", false)
        );
    }

    private ItemLore readItemLore(ConfigurationSection sec) {
        sec = orEmpty(sec);
        int maxLines = getInt(sec, "max-lines", 10);
        if (maxLines == 0) {
            plugin.getLogger().warning("'max-lines' at 'utility-defaults.item-lore' in " + fileName() + " can't be 0, using 1 (use -1 for unlimited)");
            maxLines = 1;
        }
        return new ItemLore(readItemText(sec, 50), maxLines);
    }

    private WorldRestrictions readWorldRestrictions(ConfigurationSection sec) {
        if (sec == null) {
            return new WorldRestrictions(Map.of());
        }
        Map<String, Set<RestrictedFeature>> byWorld = new LinkedHashMap<>();
        for (String world : sec.getKeys(false)) {
            Set<RestrictedFeature> features = enumSet(sec, world, RestrictedFeature.class);
            if (!features.isEmpty()) {
                byWorld.put(world, features);
            }
        }
        return new WorldRestrictions(Collections.unmodifiableMap(byWorld));
    }

    private DateTimeFormatter pattern(String raw, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid pattern '" + raw + "' in " + fileName() + ", using '" + fallback + "'");
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    private ZoneId zone(String raw) {
        if (raw == null || raw.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("Invalid timezone '" + raw + "' in " + fileName() + ", using system default");
            return ZoneId.systemDefault();
        }
    }

    // Section types

    public enum MessageFormat { MINI, LEGACY, SMART }

    public enum DurationMode { DETAILED, SEQUENTIAL, CUSTOM }

    public enum RestrictedFeature { GOD, FLY, SPEED, CUSTOM_TIME, CUSTOM_WEATHER, AFK, NEAR }

    public enum NearSort { DISTANCE, NAME }

    public enum BlockAction {
        CHAT(true),
        PRIVATE_MESSAGES(true),
        MENTIONS(true),
        TELEPORT_REQUESTS(true),
        PAYMENTS(false);

        private final boolean enabledByDefault;

        BlockAction(boolean enabledByDefault) {
            this.enabledByDefault = enabledByDefault;
        }

        public boolean enabledByDefault() {
            return enabledByDefault;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public record General(String lang, String itemsLang, boolean notifyUpdates, boolean consoleFeedback,
                          boolean strictArgs, boolean allowNicknameLookup) {}

    public record DurationStyle(DurationMode mode, int granularity) {}

    public record Formatting(MessageFormat messageFormat, DurationStyle duration, DateTimeFormatter time, DateTimeFormatter date, ZoneId timezone) {}

    public record ToggleDefaults(
            boolean msgToggle, boolean socialSpy, boolean chatToggle, boolean mentionToggle, boolean announceToggle,
            boolean payToggle, boolean payConfirmToggle, boolean tpToggle, boolean tpAutoAccept, boolean tpConfirmToggle
    ) {}

    public record BlockActions(Set<BlockAction> enabled) {

        public boolean applies(BlockAction action) {
            return enabled.contains(action);
        }
    }

    public record Blocking(int maxBlocked, BlockActions actions) {}

    public record NearHide(boolean vanished, boolean spectators, boolean afk) {}

    public record Near(int defaultRadius, int minRadius, int maxRadius, int hardMaxRadius, int maxResults, NearSort sort,
                       boolean ignoreHeight, int distanceDecimals, NearHide hide) {

        public boolean radiusUnlimited() {
            return maxRadius < 0;
        }

        public boolean resultsUnlimited() {
            return maxResults < 0;
        }
    }

    public record ItemText(int maxLength, Pattern validationRegex, List<Pattern> blacklist,
                           boolean extendProfanityWords, boolean italic) {}

    public record ItemLore(ItemText text, int maxLines) {}

    public record UtilityDefaults(Blocking blocking, Near near, ItemText itemName, ItemLore itemLore) {}

    public record WorldRestrictions(Map<String, Set<RestrictedFeature>> byWorld) {

        public boolean isRestricted(String world, RestrictedFeature feature) {
            Set<RestrictedFeature> features = byWorld.get(world);
            return features != null && features.contains(feature);
        }
    }
}