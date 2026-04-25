package com.ftxeven.aircore.util;

import com.ftxeven.aircore.AirCore;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TO_MINI = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>")
    );
    private static final Set<String> INLINE_TAGS = Set.of("sound", "actionbar", "title", "subtitle", "bossbar");
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<(/?[a-zA-Z0-9_:#!?][^>]*)>");
    private static final Map<String, Object> LANG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> STATIC_MINI_CACHE = new ConcurrentHashMap<>();
    private static final Tag EMPTY_TAG = Tag.inserting(Component.empty());
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    private static AirCore plugin;
    private static String PREFIX;
    private static boolean papiEnabled;
    private static FormatMode FORMAT_MODE = FormatMode.SMART;

    private MessageUtil() {}

    public enum FormatMode {
        MINI, LEGACY, SMART;

        public static FormatMode parse(String s) {
            try { return valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return SMART; }
        }
    }

    public static FormatMode getFormatMode() { return FORMAT_MODE; }

    public static void init(AirCore core) {
        plugin = core;
        PREFIX = String.valueOf(plugin.lang().get("general.prefix", ""));
        papiEnabled = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        FORMAT_MODE = FormatMode.parse(plugin.config().messageFormat());

        LANG_CACHE.clear();
        STATIC_MINI_CACHE.clear();
    }

    public static void send(Player player, String key, Map<String, String> placeholders) {
        if (player == null || key == null) return;
        dispatch(player, LANG_CACHE.computeIfAbsent(key, k -> plugin.lang().get(k)), placeholders);
    }

    public static void sendRaw(Player player, Object messageObj, Map<String, String> placeholders) {
        dispatch(player, messageObj, placeholders);
    }

    private static void dispatch(Player player, Object messageObj, Map<String, String> placeholders) {
        if (messageObj instanceof List<?> list) {
            for (Object line : list)
                if (line instanceof String s) processSingleMessage(player, s, placeholders);
        } else if (messageObj instanceof String s) {
            processSingleMessage(player, s, placeholders);
        }
    }

    private static void processSingleMessage(Player player, String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty() || message.equals("\"\"")) return;

        boolean hasVisibleText = false, hasTags = false;
        int depth = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if      (c == '<') { depth++; hasTags = true; }
            else if (c == '>') { depth = Math.max(0, depth - 1); }
            else if (depth == 0) { hasVisibleText = true; break; }
        }

        if (!hasVisibleText && hasTags) { mini(player, message, placeholders); return; }

        Component component = mini(player, message, placeholders);
        plugin.scheduler().runEntityTask(player, () -> player.sendMessage(component));
    }

    public static void sendSmart(Player player, Object messageObj, Map<String, String> placeholders, Component extra) {
        if (messageObj == null) return;
        List<?> lines = (messageObj instanceof List<?> l) ? l : List.of(messageObj.toString());

        for (Object lineObj : lines) {
            if (!(lineObj instanceof String raw)) continue;

            boolean hasVisibleText = false, hasTags = false;
            int depth = 0;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if      (c == '<') { depth++; hasTags = true; }
                else if (c == '>') { depth = Math.max(0, depth - 1); }
                else if (depth == 0) { hasVisibleText = true; break; }
            }

            Component base = mini(player, raw, placeholders);
            if (extra != null) base = base.replaceText(b -> b.matchLiteral("%message%").replacement(extra));
            if (!hasVisibleText && hasTags && !raw.contains("%message%")) continue;

            Component finalComp = base;
            plugin.scheduler().runEntityTask(player, () -> player.sendMessage(finalComp));
        }
    }

    public static Component mini(Player player, String raw, Map<String, String> placeholders) {
        return mini(player, raw, placeholders, true);
    }

    public static Component mini(Player player, String raw, Map<String, String> placeholders, boolean usePapi) {
        if (raw == null || raw.isBlank()) return Component.empty();

        if (PROCESSING.get()) {
            return MM.deserialize(prepare(apply(raw, player, placeholders, usePapi)));
        }

        PROCESSING.set(true);
        try {
            String applied = apply(raw, player, placeholders, usePapi);

            if (FORMAT_MODE == FormatMode.MINI && !usePapi
                    && (placeholders == null || placeholders.isEmpty())
                    && !raw.contains("<")) {
                return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
            }

            TitleState titleState = new TitleState();
            Component result = MM.deserialize(prepare(applied),
                    new InlineTagResolver(player, placeholders, titleState, usePapi));

            if (titleState.hasAny()) {
                TitleUtil.sendComponents(player, titleState.main, titleState.sub,
                        titleState.fadeIn, titleState.stay, titleState.fadeOut);
            }
            return result;
        } finally {
            PROCESSING.set(false);
        }
    }

    public static Component format(Player player, String key, Map<String, String> placeholders) {
        Object messageObj = LANG_CACHE.computeIfAbsent(key, k -> plugin.lang().get(k));
        if (messageObj instanceof String s) return mini(player, s, placeholders);
        if (messageObj instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof String s)
            return mini(player, s, placeholders);
        return Component.empty();
    }

    private static String prepare(String text) {
        return switch (FORMAT_MODE) {
            case LEGACY -> translateLegacyToMini(escapeNonInlineMiniTags(text));
            case MINI -> text;
            case SMART -> translateLegacyToMini(text);
        };
    }

    private static String escapeNonInlineMiniTags(String text) {
        if (text == null || !text.contains("<")) return text;
        Matcher m = MINI_TAG_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            String tagName = m.group(1).toLowerCase().replaceFirst("^/", "").split("[: ]")[0];
            if (INLINE_TAGS.contains(tagName)) sb.append(m.group());
            else sb.append('\\').append(m.group());
            last = m.end();
        }
        return sb.append(text.substring(last)).toString();
    }

    private static String translateLegacyToMini(String text) {
        if (text == null || !text.contains("&")) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '&') { sb.append('&'); i++; continue; }
                String tag = LEGACY_TO_MINI.get(Character.toLowerCase(next));
                if (tag != null) { sb.append(tag); i++; continue; }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String apply(String raw, Player player, Map<String, String> placeholders, boolean usePapi) {
        if (raw == null || raw.isEmpty()) return "";

        if (usePapi && raw.contains("%prefix%"))
            raw = raw.replace("%prefix%", PREFIX);

        if (placeholders != null && !placeholders.isEmpty())
            for (var entry : placeholders.entrySet())
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());

        if (usePapi && papiEnabled && player != null && raw.contains("%")) {
            try { raw = PlaceholderAPI.setPlaceholders(player, raw); }
            catch (Exception ignored) {}
        }
        return raw;
    }

    private static class TitleState {
        Component main = Component.empty(), sub = Component.empty();
        int fadeIn = 10, stay = 70, fadeOut = 20;
        boolean hasAny() { return main != Component.empty() || sub != Component.empty(); }
    }

    private record InlineTagResolver(
            Player player,
            Map<String, String> placeholders,
            TitleState titleState,
            boolean usePapi
    ) implements TagResolver {

        @Override
        public boolean has(@NotNull String name) {
            return INLINE_TAGS.contains(name);
        }

        @Override
        public Tag resolve(@NotNull String name, @NotNull ArgumentQueue args, @NotNull Context ctx) {
            return switch (name) {
                case "sound" -> {
                    SoundUtil.play(player, args.popOr("!").value(),
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f,
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f);
                    yield EMPTY_TAG;
                }
                case "actionbar" -> {
                    ActionbarUtil.send(plugin, player, args.popOr("!").value(), placeholders);
                    yield EMPTY_TAG;
                }
                case "title" -> {
                    titleState.main = MM.deserialize(apply(args.popOr("!").value(), player, placeholders, usePapi));
                    if (args.hasNext()) titleState.fadeIn  = args.pop().asInt().orElse(10);
                    if (args.hasNext()) titleState.stay    = args.pop().asInt().orElse(70);
                    if (args.hasNext()) titleState.fadeOut = args.pop().asInt().orElse(20);
                    yield EMPTY_TAG;
                }
                case "subtitle" -> {
                    titleState.sub = MM.deserialize(apply(args.popOr("!").value(), player, placeholders, usePapi));
                    yield EMPTY_TAG;
                }
                case "bossbar" -> {
                    BossbarUtil.send(player, args.popOr("!").value(), placeholders,
                            args.hasNext() ? args.pop().asInt().orElse(100) : 100,
                            args.hasNext() ? BossBar.Color.valueOf(args.pop().value().toUpperCase()) : BossBar.Color.WHITE,
                            args.hasNext() ? args.pop().value() : "PROGRESS",
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1.0f,
                            args.hasNext() && args.pop().value().equalsIgnoreCase("true"));
                    yield EMPTY_TAG;
                }
                default -> null;
            };
        }
    }
}