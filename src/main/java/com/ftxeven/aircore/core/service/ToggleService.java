package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;

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
        FLY("fly_enabled", false);

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
}