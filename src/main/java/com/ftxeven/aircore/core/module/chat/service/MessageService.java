package com.ftxeven.aircore.core.module.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
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
            if ((System.currentTimeMillis() - lastTime) > expireSeconds * 1000L) {
                lastMessaged.remove(senderId);
                lastMessageTime.remove(senderId);
                return null;
            }
        }
        return Bukkit.getPlayer(last);
    }

    private String processMessage(Player sender, String message) {
        String processed = plugin.chat().formats().sanitizeForChat(sender, message);

        if (processed.replaceAll("<[^>]+>", "").trim().isEmpty()) return null;

        if (plugin.config().pmApplyUrlFormatting()) {
            processed = plugin.chat().urls().apply(sender, processed);
        }

        if (plugin.config().pmApplyDisplayTags()) {
            Component component = plugin.chat().displayTags().apply(sender, MessageUtil.mini(sender, processed, null, false));
            processed = MiniMessage.miniMessage().serialize(component);
        }

        return processed;
    }

    private boolean checkCooldown(Player sender) {
        if (sender.hasPermission("aircore.bypass.chat.cooldown") || !plugin.config().pmApplyChatCooldown()) {
            return true;
        }
        var cd = plugin.chat().cooldowns();
        if (cd.isOnCooldown(sender)) {
            double remaining = cd.getRemaining(sender);
            String time = TimeUtil.formatSeconds(plugin, (long) Math.ceil(remaining));
            MessageUtil.send(sender, "chat.cooldown", Map.of("time", time));
            return false;
        }
        cd.apply(sender, plugin.config().chatCooldown());
        return true;
    }

    private void sendSafePM(Player viewer, String key, Map<String, String> ph, Component messageComp) {
        Component base = MessageUtil.format(viewer, key, ph);
        if (base == null) return;

        Component finalComp = base.replaceText(b -> b.matchLiteral("%message%").replacement(messageComp));

        plugin.scheduler().runEntityTask(viewer, () -> viewer.sendMessage(finalComp));
    }

    public void sendPrivateMessage(Player sender, Player target, String rawMessage) {
        if (!checkCooldown(sender)) return;

        String message = processMessage(sender, rawMessage);
        if (message == null) return;

        Component safeMsg = MessageUtil.mini(sender, message, Map.of(), false);

        Map<String, String> ph = Map.of(
                "player", sender.getName(),
                "target", target.getName()
        );

        sendSafePM(sender, "chat.private-messages.to", ph, safeMsg);
        sendSafePM(target, "chat.private-messages.from", ph, safeMsg);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || online.equals(target)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                sendSafePM(online, "chat.socialspy.to", ph, safeMsg);
            }
        }

        setReplyTarget(sender, target);
        setReplyTarget(target, sender);
    }

    public void sendPrivateMessageEveryone(Player sender, List<Player> recipients, String rawMessage) {
        if (!checkCooldown(sender)) return;

        String message = processMessage(sender, rawMessage);
        if (message == null) return;

        Component safeMsg = MessageUtil.mini(sender, message, Map.of(), false);
        Map<String, String> senderPh = Map.of("player", sender.getName());

        sendSafePM(sender, "chat.private-messages.everyone", senderPh, safeMsg);

        for (Player recipient : recipients) {
            setReplyTarget(recipient, sender);
            Map<String, String> recipientPh = Map.of(
                    "player", sender.getName(),
                    "target", recipient.getName()
            );
            sendSafePM(recipient, "chat.private-messages.from", recipientPh, safeMsg);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || recipients.contains(online)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                sendSafePM(online, "chat.socialspy.everyone", senderPh, safeMsg);
            }
        }
    }

    public void sendPrivateMessageFromConsole(String consoleName, Player target, String message) {
        if (message.isBlank()) return;

        Component safeMsg = MessageUtil.mini(null, message, Map.of(), false);
        Map<String, String> ph = Map.of("player", consoleName, "target", target.getName());

        sendSafePM(target, "chat.private-messages.from", ph, safeMsg);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                sendSafePM(online, "chat.socialspy.to", ph, safeMsg);
            }
        }
    }

    public void sendPrivateMessageEveryoneFromConsole(String consoleName, List<Player> recipients, String message) {
        if (message.isBlank()) return;

        Component safeMsg = MessageUtil.mini(null, message, Map.of(), false);
        Map<String, String> senderPh = Map.of("player", consoleName);

        for (Player recipient : recipients) {
            Map<String, String> recipientPh = Map.of("player", consoleName, "target", recipient.getName());
            sendSafePM(recipient, "chat.private-messages.from", recipientPh, safeMsg);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (recipients.contains(online)) continue;
            if (plugin.core().toggles().isEnabled(online.getUniqueId(), ToggleService.Toggle.SOCIALSPY)) {
                sendSafePM(online, "chat.socialspy.everyone", senderPh, safeMsg);
            }
        }
    }
}