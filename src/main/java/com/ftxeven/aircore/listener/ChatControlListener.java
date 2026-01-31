package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;

public final class ChatControlListener implements Listener {

    private final AirCore plugin;

    public ChatControlListener(AirCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;

        Player sender = event.getPlayer();

        // Chat cooldown
        if (!sender.hasPermission("aircore.bypass.chat.cooldown")) {
            var cd = plugin.chat().cooldowns();
            if (cd.isOnCooldown(sender)) {
                double remaining = cd.getRemaining(sender);
                if (remaining > 0) {
                    String time = TimeUtil.formatSeconds(plugin, (long) Math.ceil(remaining));
                    MessageUtil.send(sender, "chat.cooldown", Map.of("time", time));
                    event.setCancelled(true);
                    return;
                }
            }
            cd.apply(sender, plugin.config().chatCooldown());
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (!plugin.config().groupFormatEnabled()) {
            return;
        }

        Component formatted = plugin.chat().formats().format(sender, plain);

        if (formatted == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        plugin.getServer().getOnlinePlayers().forEach(recipient -> {
            if (recipient.equals(sender)) {
                recipient.sendMessage(formatted);
                return;
            }

            UUID senderId = sender.getUniqueId();
            UUID recipientId = recipient.getUniqueId();

            // Chat toggle
            if (!plugin.core().toggles().isEnabled(recipientId, ToggleService.Toggle.CHAT)) return;

            // Block list
            if (plugin.core().blocks().isBlocked(recipientId, senderId)) return;

            recipient.sendMessage(formatted);
        });

        plugin.getServer().getConsoleSender().sendMessage(formatted);
    }
}