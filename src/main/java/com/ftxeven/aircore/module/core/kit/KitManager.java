package com.ftxeven.aircore.module.core.kit;

import com.ftxeven.aircore.AirCore;

public final class KitManager {

    private final AirCore plugin;
    private KitService kitService;

    public KitManager(AirCore plugin) {
        this.plugin = plugin;
        constructServices();
    }

    public void reload() {
        constructServices();
    }

    private void constructServices() {
        this.kitService = new KitService(plugin);
    }

    public KitService kits() { return kitService; }
}
