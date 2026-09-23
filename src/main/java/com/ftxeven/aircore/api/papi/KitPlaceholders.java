package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.kits.KitClaimHandler;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

final class KitPlaceholders {

    private final KitsModule kits;
    private final ConfigManager configs;

    KitPlaceholders(KitsModule kits, ConfigManager configs) {
        this.kits = kits;
        this.configs = configs;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.equals("total")) {
            return String.valueOf(kits.findAll().size());
        }
        if (lower.startsWith("item_count_")) {
            return itemCount(key.substring("item_count_".length()));
        }
        if (lower.startsWith("one_time_")) {
            return oneTime(key.substring("one_time_".length()));
        }
        if (lower.startsWith("requires_permission_")) {
            return requiresPermission(key.substring("requires_permission_".length()));
        }

        if (viewer == null) {
            return null;
        }

        if (lower.equals("accessible")) {
            return viewer.isOnline() ? String.valueOf(kits.accessibleKitNames(viewer.getPlayer()).size()) : "0";
        }
        if (lower.startsWith("on_cooldown_")) {
            return onCooldown(viewer, key.substring("on_cooldown_".length()));
        }
        if (lower.startsWith("cooldown_")) {
            return cooldown(viewer, key.substring("cooldown_".length()));
        }
        if (lower.startsWith("has_access_")) {
            return hasAccess(viewer, key.substring("has_access_".length()));
        }
        return null;
    }

    private @Nullable String cooldown(OfflinePlayer viewer, String argument) {
        if (!viewer.isOnline()) {
            return null;
        }
        boolean raw = argument.toLowerCase(Locale.ROOT).endsWith("_raw");
        String kitId = raw ? argument.substring(0, argument.length() - "_raw".length()) : argument;

        return kits.find(kitId).map(kit -> {
            KitClaimHandler.Verdict verdict = kits.preview(viewer.getPlayer(), kit).orElse(null);
            return raw ? rawRemaining(verdict) : formatRemaining(verdict);
        }).orElse(null);
    }

    private @Nullable String onCooldown(OfflinePlayer viewer, String kitId) {
        if (!viewer.isOnline()) {
            return null;
        }
        return kits.find(kitId)
                .map(kit -> String.valueOf(blocksClaim(kits.preview(viewer.getPlayer(), kit).orElse(null))))
                .orElse(null);
    }

    private @Nullable String hasAccess(OfflinePlayer viewer, String kitId) {
        if (!viewer.isOnline()) {
            return null;
        }
        return kits.find(kitId)
                .map(kit -> String.valueOf(kits.canAccess(viewer.getPlayer(), kit)))
                .orElse(null);
    }

    private @Nullable String itemCount(String kitId) {
        return kits.find(kitId).map(kit -> String.valueOf(kit.nonEmptyItems().length)).orElse(null);
    }

    private @Nullable String oneTime(String kitId) {
        return kits.find(kitId).map(kit -> String.valueOf(kit.oneTime())).orElse(null);
    }

    private @Nullable String requiresPermission(String kitId) {
        return kits.find(kitId).map(kit -> String.valueOf(kit.requiresPermission())).orElse(null);
    }

    private boolean blocksClaim(KitClaimHandler.Verdict verdict) {
        return verdict instanceof KitClaimHandler.Verdict.OnCooldown
                || verdict instanceof KitClaimHandler.Verdict.AlreadyClaimed;
    }

    private String formatRemaining(KitClaimHandler.Verdict verdict) {
        return switch (verdict) {
            case KitClaimHandler.Verdict.OnCooldown onCooldown ->
                    TimeFormatter.duration(onCooldown.expiresAt(), configs.main().formatting(), configs.lang());
            case KitClaimHandler.Verdict.AlreadyClaimed ignored ->
                    configs.lang().get("placeholders.never").getFirst();
            case null, default ->
                    TimeFormatter.duration(0.0, configs.main().formatting(), configs.lang());
        };
    }

    private String rawRemaining(KitClaimHandler.Verdict verdict) {
        return switch (verdict) {
            case KitClaimHandler.Verdict.OnCooldown onCooldown ->
                    String.valueOf(Math.max(0, Duration.between(Instant.now(), onCooldown.expiresAt()).getSeconds()));
            case KitClaimHandler.Verdict.AlreadyClaimed ignored -> "-1";
            case null, default -> "0";
        };
    }
}