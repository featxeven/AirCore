package com.ftxeven.aircore.util;

import com.ftxeven.aircore.AirCore;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class PlaceholderUtil {

    private static final boolean HAS_PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    private static AirCore plugin;

    private PlaceholderUtil() {}

    public static void init(AirCore instance) {
        plugin = instance;
    }

    public static String apply(Player player, String text) {
        return apply(player, text, Map.of());
    }

    public static String apply(Player player, String text, Map<String, String> context) {
        if (text == null || text.isBlank()) return text;
        String result = text;

        if (player != null) {
            result = result.replace("%player%", getDisplayName(player.getUniqueId(), player.getName()));
        }

        if (context != null && !context.isEmpty()) {
            for (var entry : context.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null) continue;

                if (key.equalsIgnoreCase("target")) {
                    Player targetPlayer = Bukkit.getPlayerExact(value);
                    if (targetPlayer != null) {
                        value = getDisplayName(targetPlayer.getUniqueId(), targetPlayer.getName());
                    }
                }

                result = result.replace("%" + key + "%", value);
            }
        }

        return HAS_PAPI ? PlaceholderAPI.setPlaceholders(player, result) : result;
    }

    private static String getDisplayName(UUID uuid, String realName) {
        if (plugin == null) return realName;

        return plugin.utility().nicks().getDisplayName(uuid, realName);
    }
}