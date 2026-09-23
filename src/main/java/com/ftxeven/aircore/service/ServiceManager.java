package com.ftxeven.aircore.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.chat.filter.ProfanityFilter;
import com.ftxeven.aircore.service.inventory.InventoryService;
import com.ftxeven.aircore.service.item.HoldingService;

public final class ServiceManager {

    private final PlayerService players;
    private final ProfanityFilter profanity;
    private final InventoryService inventories;
    private final HoldingService holding;
    private final ConfirmationService confirmations;
    private final CommandCooldownService commandCooldowns;
    private final LeaderboardService leaderboards;

    public ServiceManager(AirCore plugin) {
        players = new PlayerService(plugin.cache(), plugin.database(), plugin.configs());
        profanity = new ProfanityFilter(plugin);
        inventories = new InventoryService(plugin.getLogger(), plugin.database().inventories(), plugin.cache().writes());
        holding = new HoldingService();
        confirmations = new ConfirmationService();
        commandCooldowns = new CommandCooldownService(plugin.cache(), plugin.configs());
        leaderboards = new LeaderboardService(plugin.getLogger(), plugin.configs(), players, () -> plugin.modules().variables());
    }

    public PlayerService players() { return players; }
    public ProfanityFilter profanity() { return profanity; }
    public InventoryService inventories() { return inventories; }
    public HoldingService holding() { return holding; }
    public ConfirmationService confirmations() { return confirmations; }
    public CommandCooldownService commandCooldowns() { return commandCooldowns; }
    public LeaderboardService leaderboards() { return leaderboards; }

    // Lifecycle

    public void load() {
        leaderboards.load();
    }

    public void reload() {
        load();
    }
}