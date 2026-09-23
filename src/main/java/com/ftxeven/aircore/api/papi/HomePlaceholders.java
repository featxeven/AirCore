package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.homes.HomesModule;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class HomePlaceholders {

    private final HomesModule homes;
    private final ConfigManager configs;

    HomePlaceholders(HomesModule homes, ConfigManager configs) {
        this.homes = homes;
        this.configs = configs;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.equals("icon_total")) {
            return String.valueOf(configs.filter().homeIcons().size());
        }
        if (lower.equals("icon_accessible")) {
            return (viewer != null && viewer.isOnline())
                    ? String.valueOf(accessibleIconCount(viewer.getPlayer()))
                    : "0";
        }

        if (viewer == null) {
            return null;
        }

        return switch (lower) {
            case "count" -> String.valueOf(homes.count(viewer.getUniqueId()));
            case "limit" -> String.valueOf(limit(viewer));
            case "available" -> available(viewer);
            default -> resolveAttribute(viewer, key, lower);
        };
    }

    private long accessibleIconCount(Player viewer) {
        return configs.filter().homeIcons().values().stream().filter(icon -> icon.isAccessibleTo(viewer)).count();
    }

    private @Nullable String resolveAttribute(OfflinePlayer viewer, String key, String lower) {
        HomeAttribute attribute = HomeAttribute.matching(lower);
        if (attribute == null) {
            return null;
        }
        String homeName = key.substring(0, key.length() - attribute.suffix().length());
        if (homeName.isEmpty()) {
            return null;
        }
        return homes.resolve(viewer.getUniqueId(), homeName).map(attribute::render).orElse(attribute.whenMissing());
    }

    private int limit(OfflinePlayer viewer) {
        return viewer.isOnline() ? homes.limitFor(viewer.getPlayer()) : configs.homes().general().maxHomes();
    }

    private String available(OfflinePlayer viewer) {
        int limit = limit(viewer);
        if (limit < 0) {
            return "-1";
        }
        return String.valueOf(Math.max(0, limit - homes.count(viewer.getUniqueId())));
    }

    private enum HomeAttribute {
        NAME("_name", ""), WORLD("_world", ""), X("_x", ""), Y("_y", ""), Z("_z", ""), EXISTS("_exists", "false");

        private final String suffix;
        private final String whenMissing;

        HomeAttribute(String suffix, String whenMissing) {
            this.suffix = suffix;
            this.whenMissing = whenMissing;
        }

        String suffix() { return suffix; }
        String whenMissing() { return whenMissing; }

        String render(Home home) {
            Position position = home.position();
            return switch (this) {
                case NAME -> home.name();
                case WORLD -> position.world();
                case X -> String.valueOf((long) Math.floor(position.x()));
                case Y -> String.valueOf((long) Math.floor(position.y()));
                case Z -> String.valueOf((long) Math.floor(position.z()));
                case EXISTS -> "true";
            };
        }

        static @Nullable HomeAttribute matching(String lowerKey) {
            for (HomeAttribute attribute : values()) {
                if (lowerKey.endsWith(attribute.suffix)) {
                    return attribute;
                }
            }
            return null;
        }
    }
}