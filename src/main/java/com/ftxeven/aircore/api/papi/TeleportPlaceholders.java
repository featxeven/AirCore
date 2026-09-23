package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class TeleportPlaceholders {

    private final TeleportModule teleport;
    private final ConfigManager configs;

    TeleportPlaceholders(TeleportModule teleport, ConfigManager configs) {
        this.teleport = teleport;
        this.configs = configs;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.equals("warp_total")) {
            return String.valueOf(teleport.findAllWarps().size());
        }

        if (viewer == null) {
            return null;
        }

        return switch (lower) {
            case "back_available" -> String.valueOf(teleport.back().peek(viewer.getUniqueId()).isPresent());
            case "back_count" -> String.valueOf(teleport.back().count(viewer.getUniqueId()));
            case "warp_accessible" -> viewer.isOnline() ? String.valueOf(teleport.accessibleWarpNames(viewer.getPlayer()).size()) : "0";
            case "request_limit" -> String.valueOf(requestLimit(viewer));
            default -> null;
        };
    }

    private int requestLimit(OfflinePlayer viewer) {
        return viewer.isOnline() ? teleport.maxPendingFor(viewer.getPlayer()) : configs.teleport().requests().maxPending();
    }
}