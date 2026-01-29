package com.ftxeven.aircore.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
}
