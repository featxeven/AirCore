package com.ftxeven.aircore.core.module.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DeleteKitCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.deletekit";

    public DeleteKitCommand(AirCore plugin) {
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

        String usage = plugin.commandConfig().getUsage("delkit", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        String kitName = args[0].toLowerCase();
        YamlConfiguration kitsConfig = plugin.kit().kits().getConfig();

        if (!kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of());
            return true;
        }

        kitsConfig.set("kits." + kitName, null);
        plugin.kit().kits().saveConfig();

        plugin.database().executeAsync("DELETE FROM player_kits WHERE kit = ?", ps ->
                ps.setString(1, kitName));

        MessageUtil.send(player, "kits.management.deleted", Map.of("name", kitName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            var section = plugin.kit().kits().getConfig().getConfigurationSection("kits");
            if (section == null) return Collections.emptyList();

            String input = args[0].toLowerCase();
            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        return Collections.emptyList();
    }
}