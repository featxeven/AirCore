package com.ftxeven.aircore.util;

import com.ftxeven.aircore.AirCore;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, String> LANG_RAW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> STATIC_MINI_CACHE = new ConcurrentHashMap<>();

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

        if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("%") && !raw.contains("<")) {
            return STATIC_MINI_CACHE.computeIfAbsent(raw, MM::deserialize);
        }

        TagResolver inlineResolver = createInlineResolver(player, placeholders);
        String applied = apply(raw, player, placeholders, usePapi);

        return MM.deserialize(applied, inlineResolver);
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

    private static TagResolver createInlineResolver(Player player, Map<String, String> placeholders) {
        return TagResolver.resolver(
                TagResolver.builder()
                        .tag("sound", (args, context) -> {
                            String name = args.popOr("Missing sound name").value();
                            float vol = args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f;
                            float pitch = args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f;
                            SoundUtil.play(player, name, vol, pitch);
                            return Tag.inserting(Component.empty());
                        })
                        .tag("actionbar", (args, context) -> {
                            String text = args.popOr("Missing actionbar text").value();
                            ActionbarUtil.send(plugin, player, text, placeholders);
                            return Tag.inserting(Component.empty());
                        })
                        .tag("title", (args, context) -> {
                            String title = args.popOr("Missing title text").value();
                            int fadeIn = args.hasNext() ? args.pop().asInt().orElse(10) : 10;
                            int stay = args.hasNext() ? args.pop().asInt().orElse(70) : 70;
                            int fadeOut = args.hasNext() ? args.pop().asInt().orElse(20) : 20;

                            Component titleComp = MM.deserialize(apply(title, player, placeholders));
                            TitleUtil.sendComponents(player, titleComp, Component.empty(), fadeIn, stay, fadeOut);
                            return Tag.inserting(Component.empty());
                        })
                        .tag("bossbar", (args, context) -> {
                            String text = args.popOr("Missing text").value();
                            int time = args.hasNext() ? args.pop().asInt().orElse(100) : 100;
                            BossBar.Color color = args.hasNext() ?
                                    BossBar.Color.valueOf(args.pop().value().toUpperCase()) : BossBar.Color.WHITE;

                            BossbarUtil.send(player, text,  placeholders,  time, color,  "PROGRESS",  1.0f, false);
                            return Tag.inserting(Component.empty());
                        })
                        .build()
        );
    }
}