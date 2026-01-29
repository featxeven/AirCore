package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerMoveEvent;

public final class WorldGuardListener implements Listener {

    private final AirCore plugin;
    private StateFlag flyFlag;

    public WorldGuardListener(AirCore plugin) {
        this.plugin = plugin;
        initFlag();
    }

    private void initFlag() {
        try {
            FlagRegistry registry = com.sk89q.worldguard.WorldGuard.getInstance().getFlagRegistry();
            Flag<?> f = registry.get("fly");
            if (f == null) f = registry.get("extra-flags:fly");
            if (f == null) f = registry.get("extra_flags:fly");
            if (f instanceof StateFlag) {
                flyFlag = (StateFlag) f;
            } else {
                flyFlag = null;
            }
        } catch (Throwable ignored) {
            flyFlag = null;
        }
    }

    private boolean isInFlyAllowedRegion(Player player, Location bukkitLoc) {
        if (flyFlag == null) return true;

        try {
            LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(bukkitLoc);
            ApplicableRegionSet set = query.getApplicableRegions(weLoc);
            if (set == null) return true;
            StateFlag.State state = set.queryState(lp, flyFlag);
            return state != StateFlag.State.DENY;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void forceDisableFly(Player player) {
        if (player.getAllowFlight()) {
            plugin.core().toggles().set(player.getUniqueId(), ToggleService.Toggle.FLY, false);
            player.setAllowFlight(false);
            if (player.isFlying()) {
                player.setFlying(false);
                player.setFallDistance(0f);
            }
        }
    }

    private void checkAndApply(Player player, Location loc) {
        if (!isInFlyAllowedRegion(player, loc)) {
            forceDisableFly(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        checkAndApply(event.getPlayer(), to);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        Location to = event.getTo();
        checkAndApply(p, to);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        checkAndApply(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkAndApply(event.getPlayer(), event.getPlayer().getLocation());
    }
}
