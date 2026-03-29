package com.ftxeven.aircore.listener.player;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;

public final class PlayerChatListener implements Listener {

    private final AirCore plugin;

    public PlayerChatListener(AirCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        if (!sender.hasPermission("aircore.bypass.chat.cooldown")) {
            var cd = plugin.chat().cooldowns();
            if (cd.isOnCooldown(sender)) {
                double remaining = cd.getRemaining(sender);
                String time = TimeUtil.formatSeconds(plugin, (long) Math.ceil(remaining));
                MessageUtil.send(sender, "chat.cooldown", Map.of("time", time));
                event.setCancelled(true);
                return;
            }
            cd.apply(sender, plugin.config().chatCooldown());
        }

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (!plugin.config().groupFormatEnabled()) {
            Component processedMsg = plugin.chat().formats().processMessageOnly(sender, raw);
            event.message(processedMsg);

            return;
        }

        Component formatted = plugin.chat().formats().formatFull(sender, raw);

        if (formatted == null || formatted.equals(Component.empty())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        broadcastManually(sender, formatted);
    }

    private void broadcastManually(Player sender, Component formatted) {
        final UUID senderId = sender.getUniqueId();
        plugin.getServer().getConsoleSender().sendMessage(formatted);

        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            UUID recId = recipient.getUniqueId();
            if (recId.equals(senderId)) {
                recipient.sendMessage(formatted);
                continue;
            }
            if (!plugin.core().toggles().isEnabled(recId, ToggleService.Toggle.CHAT)) continue;
            if (plugin.core().blocks().isBlocked(recId, senderId)) continue;

            recipient.sendMessage(formatted);
        }
    }
}