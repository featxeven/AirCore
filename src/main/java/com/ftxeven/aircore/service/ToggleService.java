package com.ftxeven.aircore.service;

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
        CHAT("chat_enabled"),
        MENTIONS("mentions_enabled"),
        PM("pm_enabled"),
        SOCIALSPY("socialspy_enabled"),
        PAY("pay_enabled"),
        TELEPORT("teleport_enabled"),
        GOD("god_enabled"),
        FLY("fly_enabled");

        private final String column;

        Toggle(String column) {
            this.column = column;
        }

        public String getColumn() {
            return column;
        }
    }

    public boolean toggle(UUID uuid, Toggle toggle) {
        Map<Toggle, Boolean> playerToggles = toggles.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        boolean newState = !playerToggles.getOrDefault(toggle, true);
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
                .getOrDefault(toggle, true);
    }

    public void load(UUID uuid, Map<Toggle, Boolean> states) {
        toggles.put(uuid, new ConcurrentHashMap<>(states));
    }
}