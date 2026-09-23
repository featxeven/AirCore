package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MessageComponents {

    private MessageComponents() {}

    public record Timed(String text, int fadeIn, int stay, int fadeOut) {}

    public record Sound(String key, double volume, double pitch) {}

    public sealed interface BossbarAction {
        record Show(String text, int duration, String color, String overlay, boolean countdown, double progress, boolean persist, boolean syncOnJoin) implements BossbarAction {}
        record Clear() implements BossbarAction {}
    }

    public record CommandRun(RunAs runAs, String command) {
        public enum RunAs { CONSOLE, PLAYER }
    }

    public record Bundle(List<String> chat, Timed title, String subtitle, String actionbar, BossbarAction bossbar, Sound sound) {

        public boolean hasContent() {
            return !chat.isEmpty() || title != null || subtitle != null || actionbar != null || bossbar != null || sound != null;
        }
    }

    public static Bundle read(ConfigurationSection sec) {
        return new Bundle(
                BaseFolderConfig.stringOrList(sec, "chat"),
                readTitle(sec.getConfigurationSection("title")),
                sec.getString("subtitle", null),
                sec.getString("actionbar", null),
                readBossbar(sec.getConfigurationSection("bossbar")),
                readSound(sec.getConfigurationSection("sound"))
        );
    }

    public static Timed readTitle(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return new Timed(sec.getString("text", ""), BaseFolderConfig.integer(sec, "fade-in", 10), BaseFolderConfig.integer(sec, "stay", 40), BaseFolderConfig.integer(sec, "fade-out", 10));
    }

    public static Sound readSound(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return new Sound(sec.getString("key", ""), BaseFolderConfig.decimal(sec, "volume", 1.0), BaseFolderConfig.decimal(sec, "pitch", 1.0));
    }

    public static BossbarAction readBossbar(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        if (sec.getBoolean("clear", false)) {
            return new BossbarAction.Clear();
        }
        boolean persist = sec.getBoolean("persist", false);
        return new BossbarAction.Show(
                sec.getString("text", ""),
                BaseFolderConfig.integer(sec, "duration", 5),
                sec.getString("color", "WHITE"),
                sec.getString("overlay", "PROGRESS"),
                sec.getBoolean("countdown", false),
                BaseFolderConfig.decimal(sec, "progress", 1.0),
                persist,
                sec.getBoolean("on-join", persist)
        );
    }

    public static List<CommandRun> readCommands(ConfigurationSection sec, String path) {
        if (sec == null || !sec.isSet(path)) {
            return List.of();
        }
        if (sec.isList(path)) {
            List<CommandRun> commands = new ArrayList<>();
            for (Object item : sec.getList(path)) {
                CommandRun run = readCommandRun(YamlMaps.toSection(item));
                if (run != null) {
                    commands.add(run);
                }
            }
            return List.copyOf(commands);
        }
        CommandRun run = readCommandRun(sec.getConfigurationSection(path));
        return run != null ? List.of(run) : List.of();
    }

    private static CommandRun readCommandRun(ConfigurationSection sec) {
        if (sec == null || !sec.isSet("command")) {
            return null;
        }
        CommandRun.RunAs runAs;
        try {
            runAs = CommandRun.RunAs.valueOf(sec.getString("run-as", "CONSOLE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            runAs = CommandRun.RunAs.CONSOLE;
        }
        return new CommandRun(runAs, sec.getString("command", ""));
    }
}