package com.ftxeven.aircore.core.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageService {

    private final AirCore plugin;
    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    public MessageService(AirCore plugin) {
        this.plugin = plugin;
    }

    private void setReplyTarget(Player receiver, Player sender) {
        UUID id = receiver.getUniqueId();
        lastMessaged.put(id, sender.getUniqueId());
        lastMessageTime.put(id, System.currentTimeMillis());
    }

    public Player getReplyTarget(Player sender) {
        int expireSeconds = plugin.config().pmReplyExpireSeconds();
        UUID senderId = sender.getUniqueId();
        UUID last = lastMessaged.get(senderId);
        if (last == null) return null;

        if (expireSeconds > 0) {
            long lastTime = lastMessageTime.getOrDefault(senderId, 0L);
            long now = System.currentTimeMillis();

            if ((now - lastTime) > expireSeconds * 1000L) {
                lastMessaged.remove(senderId);
                lastMessageTime.remove(senderId);
                return null;
            }
        }

        return Bukkit.getPlayer(last);
    }

    public void sendPrivateMessage(Player sender, Player target, String message) {
        if (!sender.hasPermission("aircore.bypass.chat.cooldown")
                && plugin.config().pmApplyChatCooldown()) {

            double remaining = plugin.chat().cooldowns().getRemaining(sender);
            if (remaining > 0) {
                String time = TimeUtil.formatSeconds(plugin, (long) Math.ceil(remaining));
                MessageUtil.send(sender, "chat.cooldown", Map.of("time", time));
                return;
            }
            plugin.chat().cooldowns().apply(sender, plugin.config().chatCooldown());
        }

        message = plugin.chat().formats().sanitizeForChat(sender, message);
        String stripped = message.replaceAll("<[^>]+>", "");
        if (stripped.trim().isEmpty()) return;

        if (plugin.config().pmApplyUrlFormatting()) {
            message = plugin.chat().urls().apply(sender, message);
        }

        if (plugin.config().pmApplyDisplayTags()) {
            Component component = plugin.chat().displayTags().apply(sender, MessageUtil.miniRaw(message));
            message = MiniMessage.miniMessage().serialize(component);
        }

        Map<String, String> placeholders = Map.of(
                "player", sender.getName(),
                "target", target.getName(),
                "message", message
        );

        MessageUtil.send(sender, "chat.private-messages.to", placeholders);
        MessageUtil.send(target, "chat.private-messages.from", placeholders);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || online.equals(target)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                MessageUtil.send(online, "chat.socialspy.to", placeholders);
            }
        }

        setReplyTarget(sender, target);
        setReplyTarget(target, sender);
    }

    public void sendPrivateMessageEveryone(Player sender, List<Player> recipients, String message) {
        if (!sender.hasPermission("aircore.bypass.chat.cooldown")
                && plugin.config().pmApplyChatCooldown()) {

            double remaining = plugin.chat().cooldowns().getRemaining(sender);
            if (remaining > 0) {
                String time = TimeUtil.formatSeconds(plugin, (long) Math.ceil(remaining));
                MessageUtil.send(sender, "chat.cooldown", Map.of("time", time));
                return;
            }
            plugin.chat().cooldowns().apply(sender, plugin.config().chatCooldown());
        }

        message = plugin.chat().formats().sanitizeForChat(sender, message);
        String stripped = message.replaceAll("<[^>]+>", "");
        if (stripped.trim().isEmpty()) return;

        if (plugin.config().pmApplyUrlFormatting()) {
            message = plugin.chat().urls().apply(sender, message);
        }

        if (plugin.config().pmApplyDisplayTags()) {
            Component component = plugin.chat().displayTags().apply(sender, MessageUtil.miniRaw(message));
            message = MiniMessage.miniMessage().serialize(component);
        }

        MessageUtil.send(sender, "chat.private-messages.everyone",
                Map.of("player", sender.getName(), "message", message));

        for (Player online : recipients) {
            setReplyTarget(online, sender);
            MessageUtil.send(online, "chat.private-messages.from",
                    Map.of("player", sender.getName(), "target", online.getName(), "message", message));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) continue;
            if (recipients.contains(online)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                MessageUtil.send(online, "chat.socialspy.everyone",
                        Map.of("player", sender.getName(), "message", message));
            }
        }
    }

    public void sendPrivateMessageFromConsole(String consoleName, Player target, String message) {
        String stripped = message.replaceAll("<[^>]+>", "");
        if (stripped.trim().isEmpty()) return;

        Map<String, String> placeholders = Map.of(
                "player", consoleName,
                "target", target.getName(),
                "message", message
        );

        MessageUtil.send(target, "chat.private-messages.from", placeholders);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                MessageUtil.send(online, "chat.socialspy.to", placeholders);
            }
        }
    }

    public void sendPrivateMessageEveryoneFromConsole(String consoleName, List<Player> recipients, String message) {
        String stripped = message.replaceAll("<[^>]+>", "");
        if (stripped.trim().isEmpty()) return;

        for (Player recipient : recipients) {
            MessageUtil.send(recipient, "chat.private-messages.from",
                    Map.of("player", consoleName,
                            "target", recipient.getName(),
                            "message", message));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (recipients.contains(online)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                MessageUtil.send(online, "chat.socialspy.everyone",
                        Map.of("player", consoleName,
                                "message", message));
            }
        }
    }
}
