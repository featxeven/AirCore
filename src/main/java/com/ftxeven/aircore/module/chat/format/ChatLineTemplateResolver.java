package com.ftxeven.aircore.module.chat.format;

import com.ftxeven.aircore.module.chat.LegacyCodeTranslator;
import com.ftxeven.aircore.module.chat.ChatConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ChatLineTemplateResolver {

    private static final String MESSAGE_TOKEN = "%message%";
    private static final String MESSAGE_TAG = "<message>";
    private static final String GROUP_FORMAT_TOKEN = "%group_format%";

    private final Supplier<ChatConfig> config;
    private final JavaPlugin plugin;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public ChatLineTemplateResolver(Supplier<ChatConfig> config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public String resolve(String channelKey, String channelFormat, String groupKey, String groupTemplate) {
        String cacheKey = (channelKey == null ? "-" : channelKey) + '|' + groupKey;
        return cache.computeIfAbsent(cacheKey, ignored -> build(channelFormat, groupTemplate));
    }

    private String build(String channelFormat, String groupTemplate) {
        String stitched = channelFormat != null ? channelFormat.replace(GROUP_FORMAT_TOKEN, groupTemplate) : groupTemplate;

        ChatTemplateResolver templates = new ChatTemplateResolver(config.get().format().templates(),
                (name, message) -> plugin.getLogger().warning(message + " in format.templates (modules/chat.yml)"));
        String expanded = templates.expand(stitched);

        String translated = LegacyCodeTranslator.toMiniMessage(expanded);
        return translated.replace(MESSAGE_TOKEN, MESSAGE_TAG);
    }

    public void invalidate() {
        cache.clear();
    }
}