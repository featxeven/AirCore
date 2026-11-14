package com.ftxeven.aircore.module.afk;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class AfkConfig extends BaseConfig {

    private volatile General general;
    private volatile Activity activity;
    private volatile Reason reason;
    private volatile Notice notice;
    private volatile List<Action> actionsAfter;

    public AfkConfig(JavaPlugin plugin) {
        super(plugin, "modules/afk.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        activity = readActivity(yaml.getConfigurationSection("activity"));
        reason = readReason(yaml.getConfigurationSection("reason"));
        notice = readNotice(yaml.getConfigurationSection("notify"));
        actionsAfter = readActionsAfter(yaml);
    }

    public General general() { return general; }
    public Activity activity() { return activity; }
    public Reason reason() { return reason; }
    public Notice notice() { return notice; }
    public List<Action> actionsAfter() { return actionsAfter; }

    // Section readers

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getBoolean(sec, "broadcast", true),
                getInt(sec, "idle-timeout", -1)
        );
    }

    private Activity readActivity(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Activity(
                getBoolean(sec, "move", true),
                getDouble(sec, "move-threshold", 0.15),
                getBoolean(sec, "look", true),
                getDouble(sec, "look-threshold", 10),
                getBoolean(sec, "interact", true),
                getBoolean(sec, "inventory", true),
                getBoolean(sec, "chat", true),
                getBoolean(sec, "command", true)
        );
    }

    private Reason readReason(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Reason(
                getBoolean(sec, "enabled", true),
                getInt(sec, "max-length", 100),
                readPatternList(sec, "blacklist"),
                getBoolean(sec, "extend-profanity-words", false)
        );
    }

    private Notice readNotice(ConfigurationSection sec) {
        sec = orEmpty(sec);
        ConfigurationSection actions = orEmpty(sec.getConfigurationSection("actions"));
        Set<NotifyAction> enabled = EnumSet.noneOf(NotifyAction.class);
        for (NotifyAction action : NotifyAction.values()) {
            if (getBoolean(actions, action.key(), true)) {
                enabled.add(action);
            }
        }
        return new Notice(
                getBoolean(sec, "enabled", true),
                getInt(sec, "cooldown", 30),
                Collections.unmodifiableSet(enabled)
        );
    }

    private List<Action> readActionsAfter(ConfigurationSection yaml) {
        List<Action> actions = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> raw : yaml.getMapList("actions-after")) {
            index++;
            ConfigurationSection step = new MemoryConfiguration().createSection("action", raw);
            Action action = readAction(step, index);
            if (action != null) {
                actions.add(action);
            }
        }
        return List.copyOf(actions);
    }

    private @Nullable Action readAction(ConfigurationSection step, int index) {
        if (!step.isSet("seconds")) {
            plugin.getLogger().warning("AFK action #" + index + " in " + fileName()
                    + " (actions-after) is missing 'seconds' - skipping");
            return null;
        }
        int seconds = step.getInt("seconds", -1);
        if (seconds < 0) {
            plugin.getLogger().warning("AFK action #" + index + " in " + fileName()
                    + " (actions-after) needs 'seconds' of 0 or more - skipping");
            return null;
        }

        Action action = new Action(
                seconds,
                readStringOrList(step, "conditions"),
                readStringOrList(step, "chat"),
                readTitle(step.getConfigurationSection("title")),
                step.getString("subtitle", ""),
                readSound(step.getConfigurationSection("sound")),
                readCommand(step.getConfigurationSection("command"))
        );
        if (action.isEmpty()) {
            plugin.getLogger().warning("AFK action #" + index + " in " + fileName()
                    + " (actions-after) has nothing to do - skipping");
            return null;
        }
        return action;
    }

    private @Nullable ActionTitle readTitle(@Nullable ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return new ActionTitle(
                sec.getString("text", ""),
                optionalInt(sec, "fade-in", 10),
                optionalInt(sec, "stay", 40),
                optionalInt(sec, "fade-out", 10)
        );
    }

    private @Nullable ActionSound readSound(@Nullable ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String key = sec.getString("key", "");
        if (key.isBlank()) {
            return null;
        }
        return new ActionSound(key, sec.getDouble("volume", 1.0), sec.getDouble("pitch", 1.0));
    }

    private @Nullable ActionCommand readCommand(@Nullable ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String command = sec.getString("command", "").strip();
        if (command.isEmpty()) {
            return null;
        }
        return new ActionCommand(optionalEnum(sec, "run-as", CommandSource.class, CommandSource.CONSOLE), command);
    }

    // Section types

    // What an AFK notice can be triggered by; the key is the entry under notify.actions
    public enum NotifyAction {
        PRIVATE_MESSAGES,
        MENTIONS,
        TELEPORT_REQUESTS,
        PAYMENTS;

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public enum CommandSource { CONSOLE, PLAYER }

    public record General(boolean broadcast, int idleTimeout) {

        public boolean idleEnabled() {
            return idleTimeout > 0;
        }
    }

    public record Activity(boolean move, double moveThreshold, boolean look, double lookThreshold,
                           boolean interact, boolean inventory, boolean chat, boolean command) {}

    public record Reason(boolean enabled, int maxLength, List<Pattern> blacklist, boolean extendProfanityWords) {}

    public record Notice(boolean enabled, int cooldown, Set<NotifyAction> actions) {

        public boolean applies(NotifyAction action) {
            return enabled && actions.contains(action);
        }
    }

    public record ActionTitle(String text, int fadeIn, int stay, int fadeOut) {}

    public record ActionSound(String key, double volume, double pitch) {}

    public record ActionCommand(CommandSource runAs, String command) {}

    public record Action(int seconds, List<String> conditions, List<String> chat, @Nullable ActionTitle title,
                         String subtitle, @Nullable ActionSound sound, @Nullable ActionCommand command) {

        boolean isEmpty() {
            return chat.isEmpty() && title == null && subtitle.isBlank() && sound == null && command == null;
        }
    }
}