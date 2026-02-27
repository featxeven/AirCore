package com.ftxeven.aircore.core.modules.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VirtualGuiCommand implements TabExecutor {

    private final AirCore plugin;
    private final String menuKey;
    private final MenuOpener opener;

    public VirtualGuiCommand(AirCore plugin, String menuKey, MenuOpener opener) {
        this.plugin = plugin;
        this.menuKey = menuKey;
        this.opener = opener;
    }

    @FunctionalInterface
    public interface MenuOpener {
        void open(Player player);
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

        String permission = "aircore.command.virtual." + menuKey;
        if (!player.hasPermission(permission)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", permission));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 0) {
            MessageUtil.send(player, "errors.too-many-arguments",
                    Map.of("usage", plugin.config().getUsage(menuKey, label)));
            return true;
        }

        plugin.scheduler().runEntityTask(player, () -> opener.open(player));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        return Collections.emptyList();
    }
}