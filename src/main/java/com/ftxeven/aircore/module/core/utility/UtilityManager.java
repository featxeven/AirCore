package com.ftxeven.aircore.module.core.utility;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.utility.service.*;

public final class UtilityManager {
    private final AirCore plugin;
    private SpawnService spawnService;
    private WarpService warpService;
    private final AfkService afkService;
    private final BackService backService;

    public UtilityManager(AirCore plugin) {
        this.plugin = plugin;

        this.afkService = new AfkService();
        this.backService = new BackService();

        constructServices();
    }

    public void reload() {
        constructServices();
    }

    private void constructServices() {
        this.spawnService = new SpawnService(plugin);
        this.warpService = new WarpService(plugin);
    }

    public SpawnService spawn() { return spawnService; }
    public WarpService warps() { return warpService; }
    public AfkService afk() { return afkService; }
    public BackService back() { return backService; }
}
