package com.ftxeven.aircore.core.module.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PayToggleCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.paytoggle";
    private static final String PERM_OTHERS = "aircore.command.paytoggle.others";

    public PayToggleCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleToggle(sender, args[0]);
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);

        if (args.length == 0) {
            handleToggle(player, player.getName());
            return true;
        }

        if (!hasOthers || (plugin.config().errorOnExcessArgs() && args.length > 1)) {
            String usage = plugin.commandConfig().getUsage("paytoggle", hasOthers ? "others" : null, label);
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        handleToggle(player, args[0]);
        return true;
    }

    private void handleToggle(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        UUID uuid = resolved.getUniqueId();
        boolean newState = plugin.core().toggles().toggle(uuid, ToggleService.Toggle.PAY);
        String realName = plugin.database().records().getRealName(targetName);
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (sender instanceof Player p) {
            if (uuid.equals(p.getUniqueId())) {
                MessageUtil.send(p, newState ? "economy.payments.toggles.enabled" : "economy.payments.toggles.disabled", Map.of());
            } else {
                MessageUtil.send(p, newState ? "economy.payments.toggles.enabled-for" : "economy.payments.toggles.disabled-for",
                        Map.of("player", realName));

                if (resolved.isOnline() && resolved.getPlayer() != null) {
                    MessageUtil.send(resolved.getPlayer(), newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                            Map.of("player", senderName));
                }
            }
        } else {
            sender.sendMessage("Payments for " + realName + " -> " + (newState ? "enabled" : "disabled"));
            if (resolved.isOnline() && resolved.getPlayer() != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(resolved.getPlayer(), newState ? "economy.payments.toggles.enabled-by" : "economy.payments.toggles.disabled-by",
                        Map.of("player", senderName));
            }
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        else sender.sendMessage("Player not found");
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return Collections.emptyList();

        if (sender instanceof Player player && !player.hasPermission(PERM_OTHERS)) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}