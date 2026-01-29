package com.ftxeven.aircore.module.core.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.kit.KitManager;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EditKitCommand implements TabExecutor {

    private final AirCore plugin;
    private final KitManager manager;

    public EditKitCommand(AirCore plugin, KitManager manager) {
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

        if (!player.hasPermission("aircore.command.editkit")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.editkit"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("editkit", label)));
            return true;
        }

        String kitName = args[0].toLowerCase();
        YamlConfiguration kitsConfig = manager.kits().getConfig();

        if (!kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of());
            return true;
        }

        boolean oneTime = kitsConfig.getBoolean("kits." + kitName + ".one-time", false);
        long cooldown = kitsConfig.getLong("kits." + kitName + ".cooldown", 0);

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase();

            if (arg.equals("-onetime")) {
                oneTime = true;
                continue;
            }

            Long parsed = TimeUtil.parseDurationSeconds(arg);
            if (parsed != null) {
                cooldown = parsed;
            } else {
                MessageUtil.send(player, "errors.invalid-format", Map.of());
                return true;
            }
        }

        // Override items with current inventory
        List<Map<String, Object>> items = new ArrayList<>();
        for (var stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            items.add(stack.serialize());
        }

        kitsConfig.set("kits." + kitName + ".one-time", oneTime);
        kitsConfig.set("kits." + kitName + ".cooldown", cooldown);
        kitsConfig.set("kits." + kitName + ".items", items);

        manager.kits().saveConfig();

        MessageUtil.send(player, "kits.management.edited", Map.of("name", kitName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.editkit")) return List.of();

        var section = manager.kits().getConfig().getConfigurationSection("kits");
        if (section == null) return List.of();

        if (args.length == 1) {
            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .limit(20)
                    .toList();
        }

        if (args.length == 2) {
            String kitName = args[0].toLowerCase();
            if (!section.getKeys(false).contains(kitName)) {
                return List.of();
            }

            String current = args[args.length - 1].toLowerCase();
            if ("-onetime".startsWith(current)) {
                return List.of("-onetime");
            }
        }

        return List.of();
    }
}
