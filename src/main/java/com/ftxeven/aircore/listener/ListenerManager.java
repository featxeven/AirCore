package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.listener.hook.WorldGuardListener;
import com.ftxeven.aircore.listener.player.PlayerActivityListener;
import com.ftxeven.aircore.listener.player.PlayerChatListener;
import com.ftxeven.aircore.listener.player.PlayerDeathMessageListener;
import com.ftxeven.aircore.listener.player.PlayerLifecycleListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

public final class ListenerManager {

    private final AirCore plugin;
    private final GuiListener guiListener;
    private final PlayerChatListener playerChatListener;
    private final PlayerLifecycleListener lifecycleListener;
    private final PlayerDeathMessageListener deathMessageListener;
    private final PlayerActivityListener activityListener;
    private WorldGuardListener worldGuardListener;

    public ListenerManager(AirCore plugin) {
        this.plugin = plugin;

        this.guiListener = new GuiListener(plugin.gui());
        this.playerChatListener = new PlayerChatListener(plugin);
        this.lifecycleListener = new PlayerLifecycleListener(plugin);
        this.deathMessageListener = new PlayerDeathMessageListener(plugin);
        this.activityListener = new PlayerActivityListener(plugin, plugin.scheduler());

        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                this.worldGuardListener = new WorldGuardListener(plugin);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to initialize WorldGuardListener even though plugin is enabled.");
            }
        }
    }

    public void registerAll() {
        PluginManager pm = Bukkit.getPluginManager();

        pm.registerEvents(guiListener, plugin);
        pm.registerEvents(playerChatListener, plugin);
        pm.registerEvents(lifecycleListener, plugin);
        pm.registerEvents(deathMessageListener, plugin);
        pm.registerEvents(activityListener, plugin);

        if (worldGuardListener != null) {
            pm.registerEvents(worldGuardListener, plugin);
            plugin.getLogger().info("Successfully registered WorldGuard flight protection hook.");
        }
    }

}