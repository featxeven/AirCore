package com.ftxeven.aircore.api;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.homes.HomeManager;
import com.ftxeven.aircore.core.gui.homes.HomeTargetManager;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AirCorePAPIExpansion extends PlaceholderExpansion {

    private final AirCore plugin;

    public AirCorePAPIExpansion(AirCore plugin) { this.plugin = plugin; }

    @Override
    public @NotNull String getIdentifier() { return "aircore"; }
    @Override
    public @NotNull String getAuthor() { return "ftxeven"; }
    @Override
    public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("server_afk_total")) return String.valueOf(plugin.utility().afk().getTotalAfk());
        if (params.equalsIgnoreCase("server_warp_total")) return String.valueOf(plugin.utility().warps().getTotalWarps());

        if (player == null || !plugin.database().records().hasJoinedBefore(player.getUniqueId())) return null;

        UUID uuid = player.getUniqueId();
        String pLow = params.toLowerCase();

        if (pLow.startsWith("player_toggle_")) {
            try {
                String toggleName = params.substring(14).toUpperCase();
                return bool(plugin.core().toggles().isEnabled(uuid, ToggleService.Toggle.valueOf(toggleName)));
            } catch (Exception e) { return null; }
        }

        if (pLow.equals("player_unique")) return String.valueOf(plugin.database().records().getJoinIndex(uuid));
        if (pLow.equals("player_back_available")) return bool(plugin.utility().back().getLastDeath(uuid) != null);
        if (pLow.equals("player_afk_status")) return player.isOnline() ? bool(plugin.utility().afk().isAfk(uuid)) : null;

        if (pLow.startsWith("player_balance_")) {
            double bal = plugin.database().records().getBalance(uuid);
            return switch (pLow.substring(15)) {
                case "raw" -> plugin.economy().formats().formatRaw(bal);
                case "short" -> plugin.economy().formats().formatShort(bal);
                case "formatted" -> plugin.economy().formats().formatDetailed(bal);
                default -> null;
            };
        }

        if (pLow.startsWith("player_kit_")) {
            String sub = pLow.substring(11);
            if (sub.startsWith("on_cooldown_")) return checkKit(sub.substring(12), k -> bool(plugin.kit().kits().isOnCooldown(uuid, k)));
            if (sub.startsWith("is_available_")) return checkKit(sub.substring(13), k -> bool(plugin.kit().kits().isAvailable(uuid, k)));
            if (sub.startsWith("has_permission_")) return checkKit(sub.substring(15), k -> bool(hasPerm(player, "aircore.command.kit.*") || plugin.kit().kits().hasPermission(uuid, k)));
            if (sub.startsWith("cooldown_")) {
                String[] parts = sub.substring(9).split("_", 2);
                long sec = plugin.kit().kits().getCooldownSeconds(uuid, parts[0]);
                if (sec <= 0) return "0";
                return (parts.length > 1 && parts[1].equals("raw")) ? String.valueOf(sec) : TimeUtil.formatSeconds(plugin, sec);
            }
        }

        if (pLow.equals("player_home_limit")) return getLimit(player, "home", () -> plugin.home().homes().getLimit(uuid));
        if (pLow.equals("player_block_limit")) return getLimit(player, "block", () -> plugin.core().blocks().getLimit(uuid));
        if (pLow.equals("player_home_amount")) return String.valueOf(plugin.database().homes().getHomeAmount(uuid));
        if (pLow.equals("player_block_amount")) return String.valueOf(plugin.database().blocks().load(uuid).size());
        if (pLow.equals("player_home_guipage") || pLow.equals("player_home_guipages")) {
            Player p = player.getPlayer();
            if (p == null) return "0";
            boolean isPage = pLow.endsWith("guipage");

            HomeManager.HomeHolder construction = HomeManager.CONSTRUCTION_CONTEXT.get();
            if (construction != null) {
                return String.valueOf(isPage ? construction.page() : construction.maxPages());
            }

            HomeTargetManager.HomeTargetHolder targetConstruction = HomeTargetManager.CONSTRUCTION_CONTEXT.get();
            if (targetConstruction != null) {
                return String.valueOf(isPage ? targetConstruction.page() : targetConstruction.maxPages());
            }

            Object holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof HomeManager.HomeHolder h) {
                return String.valueOf(isPage ? h.page() : h.maxPages());
            }
            if (holder instanceof HomeTargetManager.HomeTargetHolder h) {
                return String.valueOf(isPage ? h.page() : h.maxPages());
            }

            return "0";
        }

        if (pLow.startsWith("player_home_get_")) {
            var names = plugin.database().homes().getHomeNames(uuid);
            try {
                int idx = Integer.parseInt(params.substring(16)) - 1;
                return (idx >= 0 && idx < names.size()) ? names.get(idx) : "";
            } catch (Exception e) { return null; }
        }

        if (pLow.startsWith("player_block_")) {
            boolean isHas = pLow.startsWith("has_", 13);
            boolean isBy = !isHas && pLow.startsWith("by_", 13);

            if (isHas || isBy) {
                String targetName = params.substring(isHas ? 17 : 16);
                UUID target = plugin.database().records().uuidFromName(targetName);

                if (target == null) return "false";

                return isHas
                        ? bool(plugin.core().blocks().isBlocked(uuid, target))
                        : bool(plugin.core().blocks().isBlocked(target, uuid));
            }
        }

        if (pLow.startsWith("player_warp_has_permission_")) {
            String w = params.substring(27);
            return plugin.utility().warps().exists(w) ? bool(hasPerm(player, "aircore.command.warp.*") || plugin.utility().warps().hasPermission(uuid, w)) : null;
        }

        return pLow.startsWith("key_") ? plugin.placeholders().resolve(player, params.substring(4), Map.of()) : null;
    }

    private String bool(boolean v) { return v ? "true" : "false"; }

    private boolean hasPerm(OfflinePlayer p, String node) {
        var online = p.getPlayer();
        return online != null && (online.isOp() || online.hasPermission(node));
    }

    private String getLimit(OfflinePlayer p, String type, Supplier<Integer> dbFunc) {
        if (hasPerm(p, "aircore.bypass." + type + ".limit.*")) return "unlimited";
        int lim = dbFunc.get();
        return lim == Integer.MAX_VALUE ? "unlimited" : String.valueOf(lim);
    }

    private String checkKit(String name, Function<String, String> func) {
        return plugin.kit().kits().exists(name) ? func.apply(name) : null;
    }
}