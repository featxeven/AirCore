package com.ftxeven.aircore.util;

import com.ftxeven.aircore.AirCore;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, String> LANG_RAW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> STATIC_MINI_CACHE = new ConcurrentHashMap<>();

    private static AirCore plugin;
    private static SchedulerUtil scheduler;
    private static String PREFIX;
    private static final Pattern SOUND_PATTERN =
            Pattern.compile("<sound:([^:>]+):?([^:>]*):?([^:>]*)>");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title:'((?:[^']|'{2})+)':(\\d+):(\\d+):(\\d+)>");
    private static final Pattern SUBTITLE_PATTERN =
            Pattern.compile("<subtitle:'((?:[^']|'{2})+)'>");
    private static final Pattern ACTIONBAR_PATTERN =
            Pattern.compile("<actionbar:'((?:[^']|'{2})+)'>");
    private static final Pattern BOSSBAR_PATTERN =
            Pattern.compile("<bossbar:'((?:[^']|'{2})+)':(\\d+):([a-zA-Z]+):?(\\d*\\.?\\d*)?:?(true|false)?>");
    private static boolean papiEnabled;

    private MessageUtil() {}

    public static void init(AirCore core) {
        plugin = core;
        scheduler = core.scheduler();
        PREFIX = plugin.lang().get("general.prefix");
        papiEnabled = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        LANG_RAW_CACHE.clear();
        STATIC_MINI_CACHE.clear();
    }

    // Language key to formatted Component
    public static Component format(Player player, String key, Map<String, String> placeholders) {
        String raw = LANG_RAW_CACHE.computeIfAbsent(key, k -> plugin.lang().get(k));
        if (raw.isBlank()) return null;

        raw = parseInlineTags(player, raw, placeholders);

        if (!raw.contains("%")) {
            return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
        }

        String applied = apply(raw, player, placeholders);
        return MM.deserialize(applied);
    }

    // Raw MiniMessage to formatted Component
    public static Component mini(Player player, String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isBlank()) return null;

        raw = parseInlineTags(player, raw, placeholders);

        if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("%")) {
            return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
        }

        String applied = apply(raw, player, placeholders);
        return MM.deserialize(applied);
    }

    public static Component miniRaw(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return MM.deserialize(raw);
    }

    // Raw MiniMessage to formatted Component for chat contexts
    public static Component miniForChat(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isBlank()) return null;

        raw = parseInlineTags(null, raw, placeholders);

        if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("%")) {
            return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
        }

        if (raw.contains("%prefix%")) {
            raw = raw.replace("%prefix%", PREFIX);
        }
        if (placeholders != null && !placeholders.isEmpty()) {
            for (var e : placeholders.entrySet()) {
                String token = "%" + e.getKey() + "%";
                raw = raw.replace(token, e.getValue());
            }
        }

        return MM.deserialize(raw);
    }

    // Always treat raw as a language key
    public static void send(Player player, String key, Map<String,String> placeholders) {
        if (player == null || key == null || key.isEmpty()) return;

        String raw = LANG_RAW_CACHE.computeIfAbsent(key, k -> plugin.lang().get(k));
        if (raw.isBlank()) return;

        // Cached static component
        if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("%") && !raw.contains("<")) {
            Component comp = STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
            if (!comp.equals(Component.empty())) {
                sendToPlayer(player, comp);
            }
            return;
        }

        Component result = mini(player, raw, placeholders);

        if (result != null && !result.equals(Component.empty())) {
            sendToPlayer(player, result);
        }
    }

    private static void sendToPlayer(Player player, Component component) {
        if (scheduler != null) {
            scheduler.runEntityTask(player, () -> player.sendMessage(component));
        } else {
            player.sendMessage(component);
        }
    }

    private static String parseInlineTags(Player player, String raw, Map<String,String> placeholders) {
        if (raw == null || raw.isBlank()) return raw;
        if (!raw.contains("<sound:") && !raw.contains("<title:") &&
                !raw.contains("<subtitle:") && !raw.contains("<actionbar:") &&
                !raw.contains("<bossbar:")) {
            return raw;
        }

        int fadeIn = 0, stay = 40, fadeOut = 10;
        String titleText = null;
        String subtitleText = null;

        // Sound
        Matcher soundMatcher = SOUND_PATTERN.matcher(raw);
        while (soundMatcher.find()) {
            String name = soundMatcher.group(1);
            float vol   = soundMatcher.group(2).isEmpty() ? 1f : parseFloatSafe(soundMatcher.group(2));
            float pitch = soundMatcher.group(3).isEmpty() ? 1f : parseFloatSafe(soundMatcher.group(3));
            SoundUtil.play(player, name, vol, pitch);
        }
        raw = soundMatcher.replaceAll("");

        // Title
        Matcher titleMatcher = TITLE_PATTERN.matcher(raw);
        while (titleMatcher.find()) {
            titleText = titleMatcher.group(1).replace("''", "'");
            fadeIn    = parseIntSafe(titleMatcher.group(2), 0);
            stay      = parseIntSafe(titleMatcher.group(3), 40);
            fadeOut   = parseIntSafe(titleMatcher.group(4), 10);
        }
        raw = titleMatcher.replaceAll("");

        // Subtitle
        Matcher subtitleMatcher = SUBTITLE_PATTERN.matcher(raw);
        while (subtitleMatcher.find()) {
            subtitleText = subtitleMatcher.group(1).replace("''", "'");
        }
        raw = subtitleMatcher.replaceAll("");

        // If either title or subtitle was found, build Components here
        if (titleText != null || subtitleText != null) {
            Component titleComp = Component.empty();
            Component subtitleComp = Component.empty();

            if (titleText != null && !titleText.isBlank()) {
                String appliedTitle = apply(titleText, player, placeholders);
                titleComp = MM.deserialize(appliedTitle);
            }
            if (subtitleText != null && !subtitleText.isBlank()) {
                String appliedSubtitle = apply(subtitleText, player, placeholders);
                subtitleComp = MM.deserialize(appliedSubtitle);
            }

            TitleUtil.sendComponents(player, titleComp, subtitleComp, fadeIn, stay, fadeOut);
        }

        // Actionbar
        Matcher actionbarMatcher = ACTIONBAR_PATTERN.matcher(raw);
        while (actionbarMatcher.find()) {
            String text = actionbarMatcher.group(1).replace("''", "'");
            ActionbarUtil.send(plugin, player, text, placeholders);
        }
        raw = actionbarMatcher.replaceAll("");

        // Bossbar
        Matcher bossbarMatcher = BOSSBAR_PATTERN.matcher(raw);
        while (bossbarMatcher.find()) {
            String text = bossbarMatcher.group(1).replace("''", "'");
            int duration = parseIntSafe(bossbarMatcher.group(2), 100);
            String colorRaw = bossbarMatcher.group(3);

            float progress = bossbarMatcher.group(4) == null || bossbarMatcher.group(4).isEmpty()
                    ? 1.0f : parseFloatSafe(bossbarMatcher.group(4));

            boolean countdown = bossbarMatcher.group(5) != null
                    && Boolean.parseBoolean(bossbarMatcher.group(5));

            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(colorRaw.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                color = BossBar.Color.WHITE;
            }

            BossbarUtil.send(
                    plugin,
                    player,
                    text,
                    placeholders,
                    duration,
                    color,
                    BossBar.Overlay.PROGRESS,
                    progress,
                    countdown
            );
        }
        raw = bossbarMatcher.replaceAll("");

        return raw;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return def; }
    }
    private static float parseFloatSafe(String s) {
        try { return Float.parseFloat(s); } catch (Exception ignored) { return 1f; }
    }

    private static String apply(String raw, Player player, Map<String, String> placeholders) {
        if (raw == null) return null;

        if (raw.contains("%prefix%")) {
            raw = raw.replace("%prefix%", PREFIX);
        }

        if (placeholders != null && !placeholders.isEmpty()) {
            for (var e : placeholders.entrySet()) {
                String token = "%" + e.getKey() + "%";
                raw = raw.replace(token, e.getValue());
            }
        }

        if (papiEnabled && player != null && raw.contains("%")) {
            try {
                raw = PlaceholderAPI.setPlaceholders(player, raw);
            } catch (Throwable ignored) {}
        }

        return raw;
    }
}