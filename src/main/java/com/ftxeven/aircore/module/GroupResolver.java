package com.ftxeven.aircore.module;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Map;

@FunctionalInterface
public interface GroupResolver {

    String DEFAULT_KEY = "_DEFAULT_";
    GroupResolver NONE = player -> null;

    String primaryGroup(Player player);

    static GroupResolver create() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault") ? new VaultGroups() : NONE;
    }

    static <T> T resolve(Map<String, T> byGroup, String group) {
        T value = group != null ? byGroup.get(group) : null;
        return value != null ? value : byGroup.get(DEFAULT_KEY);
    }

    final class VaultGroups implements GroupResolver {

        private Permission permission;

        VaultGroups() {
            hook();
        }

        @Override
        public String primaryGroup(Player player) {
            if (permission == null && !hook()) {
                return null;
            }
            String group = permission.getPrimaryGroup(player);
            return (group == null || group.isBlank()) ? null : group;
        }

        public boolean isHooked() {
            return permission != null;
        }

        private boolean hook() {
            RegisteredServiceProvider<Permission> provider = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (provider == null) {
                return false;
            }
            permission = provider.getProvider();
            return true;
        }
    }
}