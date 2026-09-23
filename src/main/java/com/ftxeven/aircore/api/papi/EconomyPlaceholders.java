package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.service.PlayerService;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class EconomyPlaceholders {

    private final PlayerService players;
    private final EconomyModule economy;

    EconomyPlaceholders(PlayerService players, EconomyModule economy) {
        this.players = players;
        this.economy = economy;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.startsWith("total_balance")) {
            return formatted("total_balance", lower, players.totalBalance());
        }
        if (viewer != null && lower.startsWith("balance")) {
            return players.find(viewer.getUniqueId())
                    .map(profile -> formatted("balance", lower, profile.balance()))
                    .orElse(null);
        }
        return null;
    }

    private String formatted(String key, String requested, double amount) {
        Map<String, String> values = new HashMap<>();
        economy.formatter().formatInto(values, key, amount);
        return values.get(requested);
    }
}