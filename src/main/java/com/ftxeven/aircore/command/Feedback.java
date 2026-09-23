package com.ftxeven.aircore.command;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Feedback {

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;

    public Feedback(ConfigManager configs, Messenger messenger, ServiceManager services) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
    }

    // Guards

    public boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messenger.send(sender, configs.lang().get("errors.access.no-permission"), Map.of("permission", permission));
        return false;
    }

    public Optional<Player> requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        messenger.send(sender, configs.lang().get("errors.access.player-only"));
        return Optional.empty();
    }

    public <T> Optional<T> requireFound(Optional<T> resolved, CommandSender sender, String typed) {
        if (resolved.isEmpty()) {
            messenger.send(sender, configs.lang().get("errors.access.player-not-found"), Map.of("player", typed));
        }
        return resolved;
    }

    // Target feedback

    /** tells an online target what the sender did to them; silent for self-targeting and offline players. */
    public void notifyTarget(CommandSender sender, UUID targetUuid, List<String> template, Map<String, String> placeholders) {
        if (!CommandDispatch.targetFeedbackAllowed(sender, targetUuid, configs.main().general().consoleFeedback())) {
            return;
        }
        Player recipient = Bukkit.getPlayer(targetUuid);
        if (recipient == null) {
            return;
        }
        Map<String, String> withPlayer = new LinkedHashMap<>(placeholders);
        services.players().formatSender(withPlayer, "player", sender);
        messenger.send(recipient, template, withPlayer);
    }

    /** self -> message to sender only; otherwise sender gets the "for" line and the target gets the "by" line */
    public void announce(CommandSender sender, UUID targetUuid, boolean self,
                         String selfKey, String forKey, String byKey, Map<String, String> placeholders) {
        if (self) {
            messenger.send(sender, configs.lang().get(selfKey), placeholders);
            return;
        }
        Map<String, String> forPlaceholders = targetPlaceholders(targetUuid);
        forPlaceholders.putAll(placeholders);
        messenger.send(sender, configs.lang().get(forKey), forPlaceholders);
        notifyTarget(sender, targetUuid, configs.lang().get(byKey), placeholders);
    }

    public Map<String, String> targetPlaceholders(UUID targetUuid) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services.players().formatDisplayName(placeholders, "target", targetUuid);
        return placeholders;
    }

    public Map<String, String> targetPlaceholders(Player target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services.players().formatDisplayName(placeholders, "target", target); // cache-only
        return placeholders;
    }
}