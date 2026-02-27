package com.ftxeven.aircore.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlaceholderUtil {

    private static final boolean HAS_PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    private PlaceholderUtil() {}

    public static String apply(Player player, String text) {
        return apply(player, text, Map.of());
    }

    public static String apply(Player player, String text, Map<String, String> context) {
        if (text == null) return null;
        String result = text;

        if (player != null) {
            result = result.replace("%player%", player.getName());
        }

        if (context != null && !context.isEmpty()) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    result = result.replace("%" + entry.getKey() + "%", value);
                }
            }
        }

        if (HAS_PAPI) {
            return PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }
}