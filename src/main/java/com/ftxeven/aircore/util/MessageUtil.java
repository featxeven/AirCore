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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, String> LANG_RAW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> STATIC_MINI_CACHE = new ConcurrentHashMap<>();
    private static final Tag EMPTY_TAG = Tag.inserting(Component.empty());
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    private static AirCore plugin;
    private static String PREFIX;
    private static boolean papiEnabled;

    private MessageUtil() {}

    public static void init(AirCore core) {
        plugin = core;
        PREFIX = plugin.lang().get("general.prefix");
        papiEnabled = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        LANG_RAW_CACHE.clear();
        STATIC_MINI_CACHE.clear();
    }

    public static Component format(Player player, String key, Map<String, String> placeholders) {
        String raw = LANG_RAW_CACHE.computeIfAbsent(key, k -> plugin.lang().get(k));
        return mini(player, raw, placeholders);
    }

    public static Component mini(Player player, String raw, Map<String, String> placeholders, boolean usePapi) {
        if (raw == null || raw.isBlank()) return Component.empty();

        if (PROCESSING.get()) return MM.deserialize(apply(raw, player, placeholders, usePapi));

        PROCESSING.set(true);
        try {
            if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("%") && !raw.contains("<")) {
                return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
            }

            String applied = apply(raw, player, placeholders, usePapi);
            TitleState titleState = new TitleState();
            Component result = MM.deserialize(applied, new InlineTagResolver(player, placeholders, titleState));

            if (titleState.hasAny()) {
                TitleUtil.sendComponents(player, titleState.main, titleState.sub,
                        titleState.fadeIn, titleState.stay, titleState.fadeOut);
            }

            return result;
        } finally {
            PROCESSING.set(false);
        }
    }

    public static Component mini(Player player, String raw, Map<String, String> placeholders) {
        return mini(player, raw, placeholders, true);
    }

    private static String apply(String raw, Player player, Map<String, String> placeholders, boolean usePapi) {
        if (raw == null || raw.isEmpty()) return "";

        if (usePapi && raw.contains("%prefix%")) {
            raw = raw.replace("%prefix%", PREFIX);
        }

        if (placeholders != null && !placeholders.isEmpty()) {
            for (var entry : placeholders.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        if (usePapi && papiEnabled && player != null && raw.contains("%")) {
            try {
                raw = PlaceholderAPI.setPlaceholders(player, raw);
            } catch (Exception ignored) {}
        }

        return raw;
    }

    private static String apply(String raw, Player player, Map<String, String> placeholders) {
        return apply(raw, player, placeholders, true);
    }

    public static void send(Player player, String key, Map<String, String> placeholders) {
        if (player == null || key == null) return;
        Component result = format(player, key, placeholders);

        if (result != null && !result.equals(Component.empty())) {
            plugin.scheduler().runEntityTask(player, () -> player.sendMessage(result));
        }
    }

    private static class TitleState {
        Component main = Component.empty();
        Component sub = Component.empty();
        int fadeIn = 10, stay = 70, fadeOut = 20;
        boolean hasAny() { return main != Component.empty() || sub != Component.empty(); }
    }

    private record InlineTagResolver(Player player, Map<String, String> placeholders, TitleState titleState) implements TagResolver {
        @Override
        public boolean has(@NotNull String name) {
            return switch (name) {
                case "sound", "actionbar", "title", "subtitle", "bossbar" -> true;
                default -> false;
            };
        }

        @Override
        public Tag resolve(@NotNull String name, @NotNull ArgumentQueue args, @NotNull Context context) {
            switch (name) {
                case "sound" -> {
                    SoundUtil.play(player, args.popOr("!").value(),
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f,
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f);
                    return EMPTY_TAG;
                }
                case "actionbar" -> {
                    ActionbarUtil.send(plugin, player, args.popOr("!").value(), placeholders);
                    return EMPTY_TAG;
                }
                case "title" -> {
                    titleState.main = MM.deserialize(apply(args.popOr("!").value(), player, placeholders));
                    if (args.hasNext()) titleState.fadeIn = args.pop().asInt().orElse(10);
                    if (args.hasNext()) titleState.stay = args.pop().asInt().orElse(70);
                    if (args.hasNext()) titleState.fadeOut = args.pop().asInt().orElse(20);
                    return EMPTY_TAG;
                }
                case "subtitle" -> {
                    titleState.sub = MM.deserialize(apply(args.popOr("!").value(), player, placeholders));
                    return EMPTY_TAG;
                }
                case "bossbar" -> {
                    BossbarUtil.send(player, args.popOr("!").value(), placeholders,
                            args.hasNext() ? args.pop().asInt().orElse(100) : 100,
                            args.hasNext() ? BossBar.Color.valueOf(args.pop().value().toUpperCase()) : BossBar.Color.WHITE,
                            args.hasNext() ? args.pop().value() : "PROGRESS",
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1.0f,
                            args.hasNext() && args.pop().value().equalsIgnoreCase("true"));
                    return EMPTY_TAG;
                }
            }
            return null;
        }
    }
}