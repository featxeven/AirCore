package com.ftxeven.aircore.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlaceholderUtil {

    private static final boolean HAS_PAPI =
            Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    private PlaceholderUtil() {}

    public static String apply(Player player, String text) {
        if (text == null) return null;
        if (HAS_PAPI) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static String apply(Player player, String text, Map<String, String> placeholders) {
        if (text == null) return null;

        // apply internal GUI placeholders first
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        // apply external placeholders
        return apply(player, text);
    }
}