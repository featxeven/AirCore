package com.ftxeven.aircore.core.chat.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ReplyCommand implements TabExecutor {

    private final AirCore plugin;

    public ReplyCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.reply")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.reply"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("reply", label)));
            return true;
        }

        boolean bypassToggle = player.hasPermission("aircore.bypass.chat.toggle");

        Player target = plugin.chat().messages().getReplyTarget(player);
        if (target == null) {
            MessageUtil.send(player, "chat.private-messages.error-nobody", Map.of());
            return true;
        }

        if (target.equals(player)) {
            if (!plugin.config().pmAllowSelfMessage()) {
                MessageUtil.send(player, "chat.private-messages.error-self", Map.of());
                return true;
            }
        }

        if (plugin.core().blocks().isBlocked(target.getUniqueId(), player.getUniqueId())) {
            MessageUtil.send(player, "utilities.blocking.error-blocked-by",
                    Map.of("player", target.getName()));
            return true;
        }

        if (!bypassToggle && !plugin.core().toggles().isEnabled(target.getUniqueId(), ToggleService.Toggle.PM)) {
            MessageUtil.send(player, "chat.private-messages.error-disabled",
                    Map.of("player", target.getName()));
            return true;
        }

        String message = String.join(" ", args);

        String sanitized = plugin.chat().formats().sanitizeForChat(player, message);
        String stripped = sanitized.replaceAll("<[^>]+>", "").trim();

        if (stripped.isEmpty()) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("reply", label)));
            return true;
        }

        plugin.chat().messages().sendPrivateMessage(player, target, message);

        if (plugin.utility().afk().isAfk(target.getUniqueId())) {
            MessageUtil.send(player, "utilities.afk.interaction-notify",
                    Map.of("player", target.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}