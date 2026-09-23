package com.ftxeven.aircore.module.chat.channel;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChannelMembership {

    private final ConfigManager configs;
    private final Messenger messenger;
    private final PlayerService players;
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public ChannelMembership(ConfigManager configs, Messenger messenger, PlayerService players) {
        this.configs = configs;
        this.messenger = messenger;
        this.players = players;
    }

    public String current(UUID uuid) {
        return current.computeIfAbsent(uuid, this::resolveInitial);
    }

    public String current(Player player) {
        return current(player.getUniqueId());
    }

    public void switchTo(Player player, String channelKey) {
        String previousKey = current(player);
        boolean changed = !previousKey.equals(channelKey);

        if (changed) {
            announceMembershipChange(player, previousKey, "chat.channel.member-left");
        }

        current.put(player.getUniqueId(), channelKey);
        if (configs.chat().channels().rememberChannel()) {
            players.updateChatChannel(player.getUniqueId(), channelKey);
        }

        if (changed) {
            announceMembershipChange(player, channelKey, "chat.channel.member-joined");
        }
    }

    public List<Player> membersOf(String channelKey) {
        List<Player> members = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (current(online).equals(channelKey)) {
                members.add(online);
            }
        }
        return members;
    }

    public void handleQuit(UUID uuid) {
        current.remove(uuid);
    }

    // Internal

    private String resolveInitial(UUID uuid) {
        ChatConfig.Channels channels = configs.chat().channels();
        String saved = channels.rememberChannel()
                ? players.peek(uuid).map(PlayerProfile::chatChannel).orElse(null)
                : null;
        if (saved != null && channels.definitions().containsKey(saved)) {
            return saved;
        }

        // no valid saved channel yet
        String resolved = channels.defaultChannel();
        if (channels.rememberChannel()) {
            players.updateChatChannel(uuid, resolved);
        }
        return resolved;
    }

    private void announceMembershipChange(Player player, String channelKey, String langKey) {
        ChatConfig.ChannelDefinition definition = configs.chat().channels().definitions().get(channelKey);
        if (definition == null || !definition.announceMembership()) {
            return;
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        players.formatDisplayName(placeholders, "player", player.getUniqueId());
        placeholders.put("channel", channelKey);

        for (Player member : membersOf(channelKey)) {
            if (!member.getUniqueId().equals(player.getUniqueId())) {
                messenger.send(member, configs.lang().get(langKey), placeholders);
            }
        }
    }
}