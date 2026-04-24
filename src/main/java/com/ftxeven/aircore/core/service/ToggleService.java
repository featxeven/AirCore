package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ToggleService {

    private final AirCore plugin;
    private final Map<UUID, Map<Toggle, Boolean>> toggles = new ConcurrentHashMap<>();

    public ToggleService(AirCore plugin) {
        this.plugin = plugin;
    }

    public enum Toggle {
        CHAT("chat_enabled", true),
        MENTIONS("mentions_enabled", true),
        PM("pm_enabled", true),
        SOCIALSPY("socialspy_enabled", false),
        PAY("pay_enabled", true),
        TELEPORT("teleport_enabled", true),
        GOD("god_enabled", false),
        FLY("fly_enabled", false),
        ANNOUNCEMENTS("announcements_enabled", true);

        private final String column;
        private final boolean defaultValue;

        Toggle(String column, boolean defaultValue) {
            this.column = column;
            this.defaultValue = defaultValue;
        }

        public String getColumn() { return column; }
        public boolean getDefaultValue() { return defaultValue; }
    }

    public boolean toggle(UUID uuid, Toggle toggle) {
        Map<Toggle, Boolean> playerToggles = toggles.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        boolean currentState = playerToggles.getOrDefault(toggle, toggle.getDefaultValue());
        boolean newState = !currentState;

        playerToggles.put(toggle, newState);
        plugin.scheduler().runAsync(() -> plugin.database().records().setToggle(uuid, toggle.getColumn(), newState));

        return newState;
    }

    public void set(UUID uuid, Toggle toggle, boolean state) {
        Map<Toggle, Boolean> playerToggles = toggles.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        playerToggles.put(toggle, state);

        plugin.scheduler().runAsync(() -> plugin.database().records().setToggle(uuid, toggle.getColumn(), state));
    }

    public void setLocal(UUID uuid, Toggle toggle, boolean state) {
        Map<Toggle, Boolean> playerToggles = toggles.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        playerToggles.put(toggle, state);
    }

    public boolean isEnabled(UUID uuid, Toggle toggle) {
        return toggles.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>())
                .getOrDefault(toggle, toggle.getDefaultValue());
    }

    public void load(UUID uuid, Map<Toggle, Boolean> states) {
        toggles.put(uuid, new ConcurrentHashMap<>(states));
    }

    public void processCommand(CommandSender sender, String targetName, Toggle toggle, String fullPath) {
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetUuid;
        String rawName;

        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            rawName = onlineTarget.getName();
        } else {
            targetUuid = plugin.database().records().uuidFromName(targetName);
            if (targetUuid == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
                else sender.sendMessage("Player not found");
                return;
            }
            rawName = plugin.database().records().getRealName(targetName);
            if (rawName == null) rawName = targetName;
        }

        boolean newState = toggle(targetUuid, toggle);

        String targetDisplayName = plugin.utility().nicks().getDisplayName(targetUuid, rawName);
        String senderName = (sender instanceof Player p) ?
                plugin.utility().nicks().getDisplayName(p.getUniqueId(), p.getName()) :
                String.valueOf(plugin.lang().get("general.console-name"));

        String stateKey = newState ? ".enabled" : ".disabled";

        if (sender instanceof Player p) {
            if (targetUuid.equals(p.getUniqueId())) {
                MessageUtil.send(p, fullPath + stateKey, Map.of());
            } else {
                MessageUtil.send(p, fullPath + stateKey + "-for", Map.of("player", targetDisplayName));

                if (onlineTarget != null) {
                    MessageUtil.send(onlineTarget, fullPath + stateKey + "-by", Map.of("player", senderName));
                }
            }
        } else {
            sender.sendMessage(toggle.name() + " for " + targetDisplayName + " -> " + (newState ? "enabled" : "disabled"));
            if (onlineTarget != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(onlineTarget, fullPath + stateKey + "-by", Map.of("player", senderName));
            }
        }
    }
}