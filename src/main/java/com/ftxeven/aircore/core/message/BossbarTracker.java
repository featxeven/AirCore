package com.ftxeven.aircore.core.message;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossbarTracker {

    private final Map<UUID, Map<String, Entry>> active = new ConcurrentHashMap<>();

    public void show(Player player, String key, BossBar bar, @Nullable ScheduledTask task) {
        Map<String, Entry> bars = active.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>());
        Entry previous = bars.put(key, new Entry(bar, task));
        if (previous != null) {
            previous.cancel();
            player.hideBossBar(previous.bar());
        }
        player.showBossBar(bar);
    }

    public void hide(UUID uuid, String key) {
        Map<String, Entry> bars = active.get(uuid);
        if (bars == null) {
            return;
        }
        Entry entry = bars.remove(key);
        if (entry == null) {
            return;
        }
        entry.cancel();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.hideBossBar(entry.bar());
        }
    }

    public void hideAll(UUID uuid) {
        Map<String, Entry> bars = active.remove(uuid);
        if (bars == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        bars.values().forEach(entry -> {
            entry.cancel();
            if (player != null) {
                player.hideBossBar(entry.bar());
            }
        });
    }

    public boolean isActive(UUID uuid, String key, BossBar bar) {
        Map<String, Entry> bars = active.get(uuid);
        return bars != null && bars.get(key) != null && bars.get(key).bar() == bar;
    }

    private record Entry(BossBar bar, @Nullable ScheduledTask task) {
        void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }
}