package com.ftxeven.aircore.core.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.kit.KitManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class DeleteKitCommand implements TabExecutor {

    private final AirCore plugin;
    private final KitManager manager;

    public DeleteKitCommand(AirCore plugin, KitManager manager) {
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

        if (!player.hasPermission("aircore.command.deletekit")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.deletekit"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("deletekit", label)));
            return true;
        }

        String kitName = args[0].toLowerCase();
        YamlConfiguration kitsConfig = manager.kits().getConfig();

        if (!kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of());
            return true;
        }

        kitsConfig.set("kits." + kitName, null);
        manager.kits().saveConfig();

        plugin.database().executeAsync(
                "DELETE FROM player_kits WHERE kit = ?",
                ps -> ps.setString(1, kitName.toLowerCase())
        );

        MessageUtil.send(player, "kits.management.deleted", Map.of("name", kitName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.deletekit")) return List.of();

        if (args.length == 1) {
            var section = manager.kits().getConfig().getConfigurationSection("kits");
            if (section == null) return List.of();

            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }
}
