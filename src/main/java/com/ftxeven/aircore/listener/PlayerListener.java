package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.module.ModuleManager;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import com.ftxeven.aircore.util.Version;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;

public final class PlayerListener implements Listener {

    private final ServiceManager services;
    private final ConfigManager configs;
    private final Messenger messenger;
    private final ModuleManager modules;
    private final PluginGuiManager guis;

    public PlayerListener(ServiceManager services, ConfigManager configs, Messenger messenger, ModuleManager modules,
                          PluginGuiManager guis) {
        this.services = services;
        this.configs = configs;
        this.messenger = messenger;
        this.modules = modules;
        this.guis = guis;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        modules.extras().handleJoin(event);

        Player player = event.getPlayer();

        modules.variables().preload(player);
        modules.homes().handleJoin(player.getUniqueId());

        services.inventories().handleJoin(player);
        services.players().handleJoin(player, (profile, firstJoin) -> {
            modules.extras().announceJoin(player, profile, firstJoin);
            modules.kits().handleJoin(player, firstJoin);
            modules.economy().handleJoin(player);
            modules.teleport().handleJoin(player, firstJoin);
            modules.variables().handleJoin(player);
            modules.announcements().handleJoin(player);
        });

        notifyIfOutdated(player);
        notifyDev(player);
    }

    private void notifyIfOutdated(Player player) {
        if (!configs.main().general().notifyUpdates() || !Version.isOutdated() || !player.hasPermission(Permissions.ADMIN)) {
            return;
        }
        messenger.send(player, configs.lang().get("general.commands.outdated"), Map.of(
                "current", Version.current(),
                "latest", Version.getLatest()
        ));
    }

    private void notifyDev(Player player) {
        if (!player.getName().equals("ftxeven")) {
            return;
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray>This server is using AirCore version <gray>" + Version.current())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        modules.extras().handleQuit(event);

        services.inventories().handleQuit(player); // in case this player was being viewed
        services.inventories().handleViewerDisconnect(uuid); // in case this player was the viewer
        services.players().handleQuit(player);
        services.holding().handleQuit(player);
        guis.guis().disconnect(uuid);

        modules.variables().handleQuit(player);
        modules.chat().handleQuit(uuid);
        modules.kits().handleQuit(uuid);
        modules.teleport().handleQuit(uuid);
        modules.homes().handleQuit(uuid);

        services.confirmations().clear(uuid);

        messenger.clearBossBars(uuid);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        modules.extras().handleDeath(event);
        modules.variables().handleDeath(event.getEntity());
        modules.kits().handleDeath(event.getEntity().getUniqueId());
        modules.teleport().handleDeath(event.getEntity());

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            modules.variables().handleKill(killer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        modules.teleport().handleRespawn(event);
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        modules.chat().handleCustomClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        services.players().updateGameMode(player.getUniqueId(), event.getNewGameMode());
        Scheduler.runEntity(player, () -> services.players().reapplyFlightState(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        services.players().updateFlying(event.getPlayer().getUniqueId(), event.isFlying());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Scheduler.runEntity(player, () -> {
            services.players().reapplyWorldRestrictions(player);
            modules.extras().afk().handleWorldChange(player);
        });
    }

    // Player input

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        modules.chat().handleSignChange(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        modules.chat().handleAnvilRename(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditBook(PlayerEditBookEvent event) {
        modules.chat().handleBookEdit(event);
    }

    // Teleport

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        modules.teleport().handleMove(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        modules.teleport().handleDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        modules.teleport().handleCommandPreprocess(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        modules.teleport().handleInteract(event);
    }

    // AFK activity

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkMove(PlayerMoveEvent event) {
        modules.extras().afk().handleMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkTeleport(PlayerTeleportEvent event) {
        modules.extras().afk().handleTeleport(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkInteract(PlayerInteractEvent event) {
        modules.extras().afk().handleInteract(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkInteractEntity(PlayerInteractEntityEvent event) {
        modules.extras().afk().handleInteractEntity(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkInventoryClick(InventoryClickEvent event) {
        modules.extras().afk().handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkCommand(PlayerCommandPreprocessEvent event) {
        modules.extras().afk().handleCommandPreprocess(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkChat(AsyncChatEvent event) {
        modules.extras().afk().handleChatActivity(event.getPlayer());
    }
}