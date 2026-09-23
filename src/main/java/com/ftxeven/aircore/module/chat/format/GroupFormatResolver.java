package com.ftxeven.aircore.module.chat.format;

import com.ftxeven.aircore.module.GroupResolver;
import com.ftxeven.aircore.module.chat.ChatConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class GroupFormatResolver {

    private static final String DISABLED_KEY = "__disabled__";
    private static final String DISABLED_TEMPLATE = "<gray>%player%<dark_gray>: <white>%message%";
    private static final String MISSING_KEY = "__missing__";
    private static final String MISSING_TEMPLATE = "%message%";

    public record Resolved(String key, String template) {}

    private final Supplier<ChatConfig> config;
    private final GroupResolver groups;
    private final JavaPlugin plugin;
    private final AtomicBoolean warnedMissingDefault = new AtomicBoolean(false);

    public GroupFormatResolver(Supplier<ChatConfig> config, GroupResolver groups, JavaPlugin plugin) {
        this.config = config;
        this.groups = groups;
        this.plugin = plugin;
    }

    public Resolved resolve(Player player) {
        ChatConfig.Format format = config.get().format();
        if (!format.enabled()) {
            return new Resolved(DISABLED_KEY, DISABLED_TEMPLATE);
        }

        String group = groups.primaryGroup(player);
        String key = format.groups().containsKey(group) ? group : GroupResolver.DEFAULT_KEY;
        String template = format.groups().get(key);
        if (template == null) {
            if (warnedMissingDefault.compareAndSet(false, true)) {
                plugin.getLogger().warning("No _DEFAULT_ entry in format.groups (modules/chat.yml) - "
                        + "chat messages will show a bare '%message%' until this is fixed");
            }
            return new Resolved(MISSING_KEY, MISSING_TEMPLATE);
        }
        return new Resolved(key, template);
    }

    public void resetWarnings() {
        warnedMissingDefault.set(false);
    }
}