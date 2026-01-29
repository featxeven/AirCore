package com.ftxeven.aircore.api;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class AirCoreExpansion extends PlaceholderExpansion {

    private final AirCore plugin;

    public AirCoreExpansion(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "aircore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ftxeven";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Server-wide placeholders
        if (params.equalsIgnoreCase("server_afk_total")) {
            return String.valueOf(plugin.utility().afk().getTotalAfk());
        }
        if (params.equalsIgnoreCase("server_warp_total")) {
            return String.valueOf(plugin.utility().warps().getTotalWarps());
        }

        if (player == null) return null;
        UUID uuid = player.getUniqueId();

        // Avoid queries for players who never joined
        if (!plugin.database().records().hasJoinedBefore(uuid)) {
            return null;
        }

        switch (params.toLowerCase()) {
            case "player_toggle_chat":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.CHAT));
            case "player_toggle_mentions":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.MENTIONS));
            case "player_toggle_pm":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.PM));
            case "player_toggle_socialspy":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.SOCIALSPY));
            case "player_toggle_pay":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.PAY));
            case "player_toggle_teleport":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.TELEPORT));
            case "player_toggle_god":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.GOD));
            case "player_toggle_fly":
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.FLY));
        }

        if (params.equalsIgnoreCase("player_unique")) {
            Integer joinIndex = plugin.database().records().getJoinIndex(uuid);
            return joinIndex != null ? String.valueOf(joinIndex) : null;
        }

        if (params.equalsIgnoreCase("player_back_available")) {
            return bool(plugin.utility().back().getLastDeath(uuid) != null);
        }

        if (params.equalsIgnoreCase("player_afk_status")) {
            if (player.isOnline()) {
                return bool(plugin.utility().afk().isAfk(uuid));
            }
            return null;
        }

        // Format: RAW, SHORT, FORMATTED
        if (params.startsWith("player_balance_")) {
            String format = params.substring("player_balance_".length()).toUpperCase();
            double balance = plugin.database().records().getBalance(uuid);

            return switch (format) {
                case "RAW" -> plugin.economy().formats().formatRaw(balance);
                case "SHORT" -> plugin.economy().formats().formatShort(balance);
                case "FORMATTED" -> plugin.economy().formats().formatDetailed(balance);
                default -> null;
            };
        }

        if (params.startsWith("player_kit_on_cooldown_")) {
            String kitName = params.substring("player_kit_on_cooldown_".length());
            return plugin.kit().kits().exists(kitName)
                    ? bool(plugin.kit().kits().isOnCooldown(uuid, kitName))
                    : null;
        }

        if (params.startsWith("player_kit_is_available_")) {
            String kitName = params.substring("player_kit_is_available_".length());
            return plugin.kit().kits().exists(kitName)
                    ? bool(plugin.kit().kits().isAvailable(uuid, kitName))
                    : null;
        }

        if (params.startsWith("player_kit_has_permission_")) {
            String kitName = params.substring("player_kit_has_permission_".length());
            if (!plugin.kit().kits().exists(kitName)) return null;

            var online = player.isOnline() ? player.getPlayer() : null;
            if (online != null && online.hasPermission("aircore.command.kit.*")) {
                return "true";
            }
            return bool(plugin.kit().kits().hasPermission(uuid, kitName));
        }

        // Format: kit_name or kit_name_DETAILED/SEQUENTIAL/CUSTOM
        if (params.startsWith("player_kit_cooldown_")) {
            String remainder = params.substring("player_kit_cooldown_".length());
            String[] parts = remainder.split("_", 2);

            String kitName = parts[0].toLowerCase();
            String format = parts.length > 1 ? parts[1].toUpperCase() : "RAW";

            if (!plugin.kit().kits().exists(kitName)) return null;

            long seconds = plugin.kit().kits().getCooldownSeconds(uuid, kitName);
            if (seconds <= 0) return "0";

            return switch (format) {
                case "RAW" -> String.valueOf(seconds);
                case "DETAILED", "SEQUENTIAL", "CUSTOM" -> TimeUtil.formatSeconds(plugin, seconds);
                default -> null;
            };
        }

        if (params.equalsIgnoreCase("player_home_limit")) {
            var online = player.isOnline() ? player.getPlayer() : null;
            if (online != null && (online.isOp() || online.hasPermission("aircore.bypass.home.limit.*"))) {
                return "unlimited";
            }

            int limit = plugin.database().homes().getHomeLimit(uuid);
            return limit == Integer.MAX_VALUE ? "unlimited" : String.valueOf(limit);
        }

        if (params.equalsIgnoreCase("player_home_amount")) {
            return String.valueOf(plugin.database().homes().getHomeAmount(uuid));
        }

        if (params.startsWith("player_home_get_")) {
            try {
                int index = Integer.parseInt(params.substring("player_home_get_".length()));
                var names = plugin.database().homes().getHomeNames(uuid);
                return (index >= 1 && index <= names.size()) ? names.get(index - 1) : "";
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (params.equalsIgnoreCase("player_block_limit")) {
            var online = player.isOnline() ? player.getPlayer() : null;
            if (online != null && (online.isOp() || online.hasPermission("aircore.bypass.block.limit.*"))) {
                return "unlimited";
            }

            int limit = plugin.core().blocks().getBlockLimit(uuid);
            return limit == Integer.MAX_VALUE ? "unlimited" : String.valueOf(limit);
        }

        if (params.equalsIgnoreCase("player_block_amount")) {
            return String.valueOf(plugin.database().blocks().load(uuid).size());
        }

        if (params.startsWith("player_block_has_")) {
            String name = params.substring("player_block_has_".length());
            UUID targetId = plugin.database().records().uuidFromName(name);
            if (targetId == null) return null;
            return bool(plugin.core().blocks().isBlocked(uuid, targetId));
        }

        if (params.startsWith("player_block_by_")) {
            String name = params.substring("player_blocked_by_".length());
            UUID targetId = plugin.database().records().uuidFromName(name);
            if (targetId == null) return null;
            return bool(plugin.core().blocks().isBlocked(targetId, uuid));
        }

        if (params.startsWith("player_warp_has_permission_")) {
            String warpName = params.substring("player_warp_has_permission_".length());
            if (!plugin.utility().warps().exists(warpName)) return null;

            var online = player.isOnline() ? player.getPlayer() : null;
            if (online != null && online.hasPermission("aircore.command.warp.*")) {
                return "true";
            }
            return bool(plugin.utility().warps().hasPermission(uuid, warpName));
        }

        return null;
    }

    private String bool(boolean value) {
        return value ? "true" : "false";
    }
}