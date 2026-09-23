package com.ftxeven.aircore.module.chat.channel;

import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ChannelResolver {

    private final Supplier<ChatConfig> config;
    private final ChannelMembership membership;

    public ChannelResolver(Supplier<ChatConfig> config, ChannelMembership membership) {
        this.config = config;
        this.membership = membership;
    }

    public record Resolution(String channelKey, ChatConfig.ChannelDefinition definition, String body) {}

    public Optional<Resolution> resolve(Player player, String rawMessage) {
        ChatConfig.Channels channels = config.get().channels();
        if (!channels.enabled()) {
            return Optional.empty();
        }

        Map<String, ChatConfig.ChannelDefinition> definitions = channels.definitions();
        if (definitions.isEmpty()) {
            return Optional.empty();
        }

        Optional<Resolution> prefixed = resolveByPrefix(definitions, player, rawMessage);
        if (prefixed.isPresent()) {
            return prefixed;
        }

        String key = membership.current(player);
        ChatConfig.ChannelDefinition definition = definitions.get(key);
        if (definition == null) {
            key = definitions.keySet().iterator().next();
            definition = definitions.get(key);
        }
        return Optional.of(new Resolution(key, definition, rawMessage));
    }

    private Optional<Resolution> resolveByPrefix(Map<String, ChatConfig.ChannelDefinition> definitions, Player player, String rawMessage) {
        return definitions.entrySet().stream()
                .filter(e -> e.getValue().prefix() != null && !e.getValue().prefix().isEmpty())
                .sorted(Comparator.comparingInt((Map.Entry<String, ChatConfig.ChannelDefinition> e) -> e.getValue().prefix().length()).reversed())
                .filter(e -> rawMessage.startsWith(e.getValue().prefix()))
                .filter(e -> Permissions.Access.hasChannel(player, e.getKey()))
                .findFirst()
                .map(e -> new Resolution(e.getKey(), e.getValue(), rawMessage.substring(e.getValue().prefix().length())));
    }
}