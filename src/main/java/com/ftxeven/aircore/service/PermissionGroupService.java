package com.ftxeven.aircore.service;

import com.ftxeven.aircore.AirCore;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;

import java.util.*;

public class PermissionGroupService {

    private final AirCore plugin;
    private final Map<UUID, PermissionAttachment> playerAttachments = new HashMap<>();

    public PermissionGroupService(AirCore plugin) {
        this.plugin = plugin;
        registerPermissions();
    }

    public void registerPermissions() {
        ConfigurationSection groupsSection = plugin.getConfig().getConfigurationSection("permission-groups");
        if (groupsSection == null) return;

        for (String groupName : groupsSection.getKeys(false)) {
            List<String> permissions = plugin.config().permissionGroups(groupName);
            if (permissions.isEmpty()) continue;

            String permissionName = "aircore.group." + groupName;

            Map<String, Boolean> children = new HashMap<>();
            for (String perm : permissions) {
                children.put(perm, true);
            }

            Permission existing = Bukkit.getPluginManager().getPermission(permissionName);
            if (existing != null) {
                existing.getChildren().clear();
                existing.getChildren().putAll(children);
                existing.recalculatePermissibles();
            } else {
                Permission newPerm = new Permission(
                        permissionName,
                        "Group: " + groupName,
                        PermissionDefault.FALSE,
                        children
                );
                Bukkit.getPluginManager().addPermission(newPerm);
            }
        }
    }

    public void apply(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove old attachment if it exists
        PermissionAttachment oldAttachment = playerAttachments.remove(uuid);
        if (oldAttachment != null) {
            try {
                player.removeAttachment(oldAttachment);
            } catch (IllegalArgumentException e) {
                // Attachment is invalid, ignore
            }
        }

        String primaryGroup = getPrimaryGroup(player);

        if (primaryGroup != null) {
            PermissionAttachment attachment = player.addAttachment(plugin);

            String groupPermission = "aircore.group." + primaryGroup.toLowerCase();
            attachment.setPermission(groupPermission, true);

            playerAttachments.put(uuid, attachment);
        }
    }

    private String getPrimaryGroup(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }

        try {
            var api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get primary group for " + player.getName() + ": " + e.getMessage());
        }
        return null;
    }

    public void clearAttachment(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = playerAttachments.remove(uuid);
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException e) {
                // Attachment is invalid, ignore
            }
        }
    }
}