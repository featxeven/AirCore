package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AfkCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.afk";

    public AfkCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERMISSION));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            String usage = plugin.commandConfig().getUsage("afk", label);
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (plugin.utility().afk().wasRecentlyCleared(uuid)) {
            return true;
        }

        if (plugin.utility().afk().isAfk(uuid)) {
            handleStopAfk(player);
        } else {
            handleStartAfk(player);
        }

        return true;
    }

    private void handleStartAfk(Player player) {
        plugin.utility().afk().setAfk(player.getUniqueId());
        MessageUtil.send(player, "utilities.afk.set", Map.of());

        String name = player.getName();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.hasPermission("aircore.command.afk.notify")) {
                MessageUtil.send(other, "utilities.afk.set-notify", Map.of("player", name));
            }
        }
    }

    private void handleStopAfk(Player player) {
        long elapsed = plugin.utility().afk().clearAfk(player.getUniqueId());
        String timeStr = TimeUtil.formatSeconds(plugin, elapsed);

        MessageUtil.send(player, "utilities.afk.stop", Map.of("time", timeStr));

        String name = player.getName();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.hasPermission("aircore.command.afk.notify")) {
                MessageUtil.send(other, "utilities.afk.stop-notify",
                        Map.of("player", name, "time", timeStr));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        return Collections.emptyList();
    }
}