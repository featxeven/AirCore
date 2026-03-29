package com.ftxeven.aircore.core.module.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EditKitCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.editkit";

    public EditKitCommand(AirCore plugin) {
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

        String usage = plugin.commandConfig().getUsage("editkit", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        String kitName = args[0].toLowerCase();
        YamlConfiguration kitsConfig = plugin.kit().kits().getConfig();
        String path = "kits." + kitName;

        if (!kitsConfig.contains(path)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of("name", kitName));
            return true;
        }

        boolean oneTime = kitsConfig.getBoolean(path + ".one-time", false);
        boolean autoEquip = kitsConfig.getBoolean(path + ".auto-equip", false);
        long cooldown = kitsConfig.getLong(path + ".cooldown", 0);

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("-onetime")) {
                oneTime = true;
            } else if (arg.equals("-autoequip")) {
                autoEquip = true;
            } else {
                Long parsed = TimeUtil.parseDurationSeconds(arg);
                if (parsed != null) {
                    cooldown = parsed;
                } else {
                    MessageUtil.send(player, "errors.invalid-format", Map.of("input", arg));
                    return true;
                }
            }
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            items.add(stack.clone());
        }

        kitsConfig.set(path + ".one-time", oneTime);
        kitsConfig.set(path + ".auto-equip", autoEquip);
        kitsConfig.set(path + ".cooldown", cooldown);
        kitsConfig.set(path + ".items", items);

        plugin.kit().kits().saveConfig();
        MessageUtil.send(player, "kits.management.edited", Map.of("name", kitName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) return Collections.emptyList();

        ConfigurationSection section = plugin.kit().kits().getConfig().getConfigurationSection("kits");
        if (section == null) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList();
        }

        List<String> suggestions = new ArrayList<>();
        List<String> currentArgs = List.of(args).subList(0, args.length - 1);

        if (!currentArgs.contains("-onetime") && "-onetime".startsWith(input)) {
            suggestions.add("-onetime");
        }
        if (!currentArgs.contains("-autoequip") && "-autoequip".startsWith(input)) {
            suggestions.add("-autoequip");
        }

        return suggestions;
    }
}