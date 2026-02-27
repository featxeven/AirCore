package com.ftxeven.aircore.core;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.config.ItemTranslationManager;
import com.ftxeven.aircore.core.service.*;
import com.ftxeven.aircore.util.SchedulerUtil;

public final class CoreManager {

    private final AirCore plugin;
    private final BlockService blockService;
    private final ToggleService toggleService;
    private final TeleportService teleportService;
    private final CommandCooldownService commandCooldownService;
    private ItemTranslationManager itemTranslationManager;

    public CoreManager(AirCore plugin, SchedulerUtil scheduler) {
        this.plugin = plugin;

        this.blockService = new BlockService(plugin);
        this.toggleService = new ToggleService(plugin);
        this.teleportService = new TeleportService(plugin, scheduler);
        this.commandCooldownService = new CommandCooldownService(plugin);

        constructServices();
    }

    public void reload() {
        constructServices();
    }

    private void constructServices() {
        this.itemTranslationManager = new ItemTranslationManager(plugin);
    }

    public BlockService blocks() { return blockService; }
    public ToggleService toggles() { return toggleService; }
    public TeleportService teleports() { return teleportService; }
    public CommandCooldownService commandCooldowns() { return commandCooldownService; }
    public ItemTranslationManager itemTranslations() { return itemTranslationManager; }
}