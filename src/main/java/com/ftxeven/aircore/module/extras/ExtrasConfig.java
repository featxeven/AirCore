package com.ftxeven.aircore.module.extras;

import com.ftxeven.aircore.config.BaseConfig;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.config.YamlMaps;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ExtrasConfig extends BaseConfig {

    private volatile boolean enabled;
    private volatile Join join;
    private volatile Leave leave;
    private volatile Death death;
    private volatile Afk afk;
    private volatile Nicknames nicknames;
    private volatile Block block;

    public ExtrasConfig(JavaPlugin plugin) {
        super(plugin, "modules/extras.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        enabled = getBoolean(yaml, "enabled", true);
        join = readJoin(yaml.getConfigurationSection("join"));
        leave = readLeave(yaml.getConfigurationSection("leave"));
        death = readDeath(yaml.getConfigurationSection("death"));
        afk = readAfk(yaml.getConfigurationSection("afk"));
        nicknames = readNicknames(yaml.getConfigurationSection("nicknames"));
        block = readBlock(yaml.getConfigurationSection("block"));
    }

    public boolean enabled() { return enabled; }
    public Join join() { return join; }
    public Leave leave() { return leave; }
    public Death death() { return death; }
    public Afk afk() { return afk; }
    public Nicknames nicknames() { return nicknames; }
    public Block block() { return block; }

    // Section readers

    private Join readJoin(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Join(
                getBoolean(sec, "enabled", true),
                readMotd(sec.getConfigurationSection("motd")),
                readJoinBroadcast(sec.getConfigurationSection("broadcast"))
        );
    }

    private Motd readMotd(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Motd(getStringList(sec, "first-join"), readStringListMap(sec.getConfigurationSection("returning")));
    }

    private JoinBroadcast readJoinBroadcast(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new JoinBroadcast(getString(sec, "first-join", ""), readStringMap(sec.getConfigurationSection("returning")));
    }

    private Leave readLeave(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Leave(getBoolean(sec, "enabled", true), readStringMap(sec.getConfigurationSection("broadcast")));
    }

    private Death readDeath(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Death(getBoolean(sec, "enabled", true), getStringList(sec, "disabled-worlds"));
    }

    private Afk readAfk(ConfigurationSection sec) {
        sec = orEmpty(sec);
        int idleTimeout = getInt(sec, "idle-timeout", -1);
        return new Afk(
                getBoolean(sec, "broadcast", true),
                idleTimeout > 0 ? idleTimeout : -1,
                readAfkActivity(sec.getConfigurationSection("activity")),
                readAfkReason(sec.getConfigurationSection("reason")),
                readAfkNotify(sec.getConfigurationSection("notify")),
                readAfkActions(sec.getList("actions-after"))
        );
    }

    private AfkActivity readAfkActivity(ConfigurationSection sec) {
        sec = orEmpty(sec);
        AfkActivity activity = new AfkActivity(
                getBoolean(sec, "move", true),
                Math.max(0.0, getDouble(sec, "move-threshold", 0.15)),
                getBoolean(sec, "look", true),
                Math.max(0.0, getDouble(sec, "look-threshold", 10)),
                getBoolean(sec, "interact", true),
                getBoolean(sec, "inventory", true),
                getBoolean(sec, "chat", true),
                getBoolean(sec, "command", true)
        );
        if (!activity.any()) {
            plugin.getLogger().warning("No trigger is enabled under 'afk.activity' in " + fileName()
                    + " - players will only stop being AFK by running /afk again");
        }
        return activity;
    }

    private AfkReason readAfkReason(ConfigurationSection sec) {
        sec = orEmpty(sec);
        int maxLength = getInt(sec, "max-length", 100);
        return new AfkReason(
                getBoolean(sec, "enabled", true),
                maxLength > 0 ? maxLength : -1,
                readPatternList(sec, "blacklist"),
                getBoolean(sec, "extend-profanity-words", false)
        );
    }

    private AfkNotify readAfkNotify(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new AfkNotify(
                getBoolean(sec, "enabled", true),
                getInt(sec, "cooldown", 30),
                readAfkNotifyActions(sec.getConfigurationSection("actions"))
        );
    }

    private AfkNotifyActions readAfkNotifyActions(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new AfkNotifyActions(
                getBoolean(sec, "private-messages", true),
                getBoolean(sec, "mentions", true),
                getBoolean(sec, "teleport-requests", true),
                getBoolean(sec, "payments", true)
        );
    }

    private List<AfkAction> readAfkActions(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<AfkAction> actions = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int position = i + 1;
            if (!(raw.get(i) instanceof Map<?, ?>)) {
                plugin.getLogger().warning("AFK action #" + position + " in " + fileName()
                        + " (afk.actions-after) is not a valid map, skipping");
                continue;
            }
            ConfigurationSection step = YamlMaps.toSection(raw.get(i));
            int seconds = optionalInt(step, "seconds", -1);
            if (seconds <= 0) {
                plugin.getLogger().warning("AFK action #" + position + " in " + fileName()
                        + " (afk.actions-after) needs a 'seconds' value greater than 0, skipping");
                continue;
            }
            AfkAction action = new AfkAction(
                    seconds,
                    readStringOrList(step, "conditions"),
                    MessageComponents.read(step),
                    MessageComponents.readCommands(step, "command")
            );
            if (!action.hasContent()) {
                plugin.getLogger().warning("AFK action #" + position + " in " + fileName()
                        + " (afk.actions-after) has nothing to do - skipping");
                continue;
            }
            actions.add(action);
        }
        actions.sort(Comparator.comparingInt(AfkAction::seconds));
        return List.copyOf(actions);
    }

    private Nicknames readNicknames(ConfigurationSection sec) {
        sec = orEmpty(sec);
        var validation = readNameValidation(sec);
        return new Nicknames(
                getString(sec, "prefix", "~"),
                Math.max(1, validation.maxLength()),
                validation.validationRegex(),
                validation.blacklist(),
                validation.extendProfanityWords()
        );
    }

    private Block readBlock(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Block(getInt(sec, "max-blocked", 20), readBlockActions(sec.getConfigurationSection("actions")));
    }

    private BlockActions readBlockActions(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new BlockActions(
                getBoolean(sec, "chat", true),
                getBoolean(sec, "private-messages", true),
                getBoolean(sec, "mentions", true),
                getBoolean(sec, "teleport-requests", true),
                getBoolean(sec, "payments", true)
        );
    }

    // Section types

    public enum BlockAction { CHAT, PRIVATE_MESSAGES, MENTIONS, TELEPORT_REQUESTS, PAYMENTS }

    public enum AfkNotifyAction { PRIVATE_MESSAGES, MENTIONS, TELEPORT_REQUESTS, PAYMENTS }

    // Join / leave / death

    public record Motd(List<String> firstJoin, Map<String, List<String>> returning) {}

    public record JoinBroadcast(String firstJoin, Map<String, String> returning) {}

    public record Join(boolean enabled, Motd motd, JoinBroadcast broadcast) {}

    public record Leave(boolean enabled, Map<String, String> broadcast) {}

    public record Death(boolean enabled, List<String> disabledWorlds) {}

    // AFK

    public record AfkActivity(boolean move, double moveThreshold, boolean look, double lookThreshold,
                              boolean interact, boolean inventory, boolean chat, boolean command) {

        public boolean any() {
            return move || look || interact || inventory || chat || command;
        }
    }

    public record AfkReason(boolean enabled, int maxLength, List<Pattern> blacklist, boolean extendProfanityWords) {}

    public record AfkNotifyActions(boolean privateMessages, boolean mentions, boolean teleportRequests, boolean payments) {

        public boolean applies(AfkNotifyAction action) {
            return switch (action) {
                case PRIVATE_MESSAGES -> privateMessages;
                case MENTIONS -> mentions;
                case TELEPORT_REQUESTS -> teleportRequests;
                case PAYMENTS -> payments;
            };
        }
    }

    public record AfkNotify(boolean enabled, int cooldown, AfkNotifyActions actions) {

        public boolean applies(AfkNotifyAction action) {
            return enabled && actions.applies(action);
        }
    }

    // sorted by ascending seconds
    public record AfkAction(int seconds, List<String> conditions, MessageComponents.Bundle message,
                            List<MessageComponents.CommandRun> command) {

        public boolean hasContent() {
            return message.hasContent() || !command.isEmpty();
        }
    }

    public record Afk(boolean broadcast, int idleTimeout, AfkActivity activity, AfkReason reason,
                      AfkNotify notice, List<AfkAction> actionsAfter) {

        public boolean idleEnabled() {
            return idleTimeout > 0;
        }
    }

    // Nicknames

    public record Nicknames(String prefix, int maxLength, Pattern validationRegex, List<Pattern> blacklist, boolean extendProfanityWords) {}

    // Block

    public record BlockActions(boolean chat, boolean privateMessages, boolean mentions, boolean teleportRequests, boolean payments) {

        public boolean applies(BlockAction action) {
            return switch (action) {
                case CHAT -> chat;
                case PRIVATE_MESSAGES -> privateMessages;
                case MENTIONS -> mentions;
                case TELEPORT_REQUESTS -> teleportRequests;
                case PAYMENTS -> payments;
            };
        }
    }

    public record Block(int maxBlocked, BlockActions actions) {}
}