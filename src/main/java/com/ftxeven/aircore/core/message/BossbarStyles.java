package com.ftxeven.aircore.core.message;

import net.kyori.adventure.bossbar.BossBar;

import java.util.Locale;
import java.util.logging.Logger;

public final class BossbarStyles {

    private BossbarStyles() {}

    public static BossBar.Color color(String raw, Logger logger) {
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid bossbar color '" + raw + "', using WHITE");
            return BossBar.Color.WHITE;
        }
    }

    public static BossBar.Overlay overlay(String raw, Logger logger) {
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid bossbar overlay '" + raw + "', using PROGRESS");
            return BossBar.Overlay.PROGRESS;
        }
    }
}