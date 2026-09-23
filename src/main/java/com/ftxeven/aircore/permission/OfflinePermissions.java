package com.ftxeven.aircore.permission;

import com.ftxeven.aircore.util.Scheduler;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public final class OfflinePermissions {

    private OfflinePermissions() {
    }

    public static void has(UUID uuid, String node, Consumer<Boolean> callback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            boolean scheduled = Scheduler.runEntity(online,
                    () -> callback.accept(online.hasPermission(node)),
                    () -> resolveAsync(uuid, node, callback) // player left before the task ran
            ).isPresent();
            if (scheduled) {
                return;
            }
        }
        resolveAsync(uuid, node, callback);
    }

    public static boolean hasOffline(UUID uuid, String node) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.isOp()) {
            return true;
        }
        Permission provider = provider();
        return provider != null && provider.playerHas(null, offline, node);
    }

    private static void resolveAsync(UUID uuid, String node, Consumer<Boolean> callback) {
        Scheduler.runAsync(() -> callback.accept(hasOffline(uuid, node)));
    }

    private static @Nullable Permission provider() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
        return registration != null ? registration.getProvider() : null;
    }
}