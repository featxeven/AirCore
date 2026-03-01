package com.ftxeven.aircore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BossbarUtil {

    private static final Map<UUID, Set<BossBar>> ACTIVE_BARS = new ConcurrentHashMap<>();
    private static SchedulerUtil scheduler;

    private BossbarUtil() {}

    public static void init(@NotNull SchedulerUtil schedulerUtil) {
        scheduler = schedulerUtil;
    }

    public static void send(@NotNull Player player,
                            @NotNull String message,
                            @Nullable Map<String, String> placeholders,
                            int duration,
                            @Nullable Color color,
                            @Nullable String overlayStr,
                            float progress,
                            boolean countdown) {

        if (message.isBlank()) return;

        Component comp = MessageUtil.mini(player, message, placeholders);
        if (comp == null) return;

        Overlay overlay = parseOverlay(overlayStr);

        BossBar bar = BossBar.bossBar(
                comp,
                Math.clamp(progress, 0f, 1f),
                color == null ? Color.WHITE : color,
                overlay
        );

        ACTIVE_BARS.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(bar);
        scheduler.runEntityTask(player, () -> player.showBossBar(bar));

        if (duration <= 0) return;

        if (countdown) {
            handleCountdown(player, bar, duration);
        } else {
            scheduler.runAsyncDelayed(() -> removeBar(player, bar), duration);
        }
    }

    private static void handleCountdown(@NotNull Player player, @NotNull BossBar bar, int duration) {
        final AtomicInteger ticks = new AtomicInteger(0);
        final float totalDuration = (float) duration;

        scheduler.runEntityTimer(player, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            int current = ticks.incrementAndGet();
            float newProgress = Math.clamp(1f - (current / totalDuration), 0f, 1f);
            bar.progress(newProgress);

            if (current >= duration) {
                removeBar(player, bar);
                task.cancel();
            }
        }, 1L, 1L);
    }

    public static void hideAll(@NotNull Player player) {
        Set<BossBar> bars = ACTIVE_BARS.remove(player.getUniqueId());
        if (bars != null) {
            scheduler.runEntityTask(player, () -> {
                for (BossBar bar : bars) {
                    player.hideBossBar(bar);
                }
            });
        }
    }

    public static void hideAll() {
        for (UUID uuid : ACTIVE_BARS.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hideAll(player);
            }
        }
        ACTIVE_BARS.clear();
    }

    private static void removeBar(@NotNull Player player, @NotNull BossBar bar) {
        UUID uuid = player.getUniqueId();
        scheduler.runEntityTask(player, () -> player.hideBossBar(bar));

        Set<BossBar> bars = ACTIVE_BARS.get(uuid);
        if (bars != null) {
            bars.remove(bar);
            if (bars.isEmpty()) ACTIVE_BARS.remove(uuid);
        }
    }

    private static Overlay parseOverlay(@Nullable String input) {
        if (input == null || input.isBlank()) return Overlay.PROGRESS;
        String normalized = input.toUpperCase().trim().replace("SOLID", "PROGRESS");
        try {
            return Overlay.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return Overlay.PROGRESS;
        }
    }
}