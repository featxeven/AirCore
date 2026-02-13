package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.utility.UtilityManager;
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

public final class AfkCommand implements TabExecutor {
    private final AirCore plugin;
    private final UtilityManager manager;

    public AfkCommand(AirCore plugin, UtilityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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

        if (!player.hasPermission("aircore.command.afk")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.afk"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("afk", label)));
            return true;
        }

        if (manager.afk().wasRecentlyCleared(player.getUniqueId())) {
            return true;
        }

        if (manager.afk().isAfk(player.getUniqueId())) {
            stopAfk(player);
        } else {
            startAfk(player);
        }

        return true;
    }

    private void startAfk(Player player) {
        manager.afk().setAfk(player.getUniqueId());
        MessageUtil.send(player, "utilities.afk.set", Map.of());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.hasPermission("aircore.command.afk.notify")) {
                MessageUtil.send(other, "utilities.afk.set-notify", Map.of("player", player.getName()));
            }
        }
    }

    private void stopAfk(Player player) {
        long elapsed = manager.afk().clearAfk(player.getUniqueId());
        String timeStr = TimeUtil.formatSeconds(plugin, elapsed);

        MessageUtil.send(player, "utilities.afk.stop", Map.of("time", timeStr));

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (other.hasPermission("aircore.command.afk.notify")) {
                MessageUtil.send(other, "utilities.afk.stop-notify",
                        Map.of("player", player.getName(), "time", timeStr));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}