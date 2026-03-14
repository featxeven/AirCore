package com.ftxeven.aircore.core.module.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CreateKitCommand implements TabExecutor {

    private final AirCore plugin;

    public CreateKitCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }
        if (!player.hasPermission("aircore.command.createkit")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.createkit"));
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("createkit", label)));
            return true;
        }
        String kitName = args[0].toLowerCase();
        var kitsConfig = plugin.kit().kits().getConfig();
        if (kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.management.already-exists", Map.of("name", kitName));
            return true;
        }
        boolean oneTime = false;
        boolean autoEquip = false;
        Long cooldown = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("-onetime")) {
                oneTime = true;
                continue;
            }
            if (arg.equals("-autoequip")) {
                autoEquip = true;
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
        if (cooldown == null) cooldown = 0L;
        boolean success = plugin.kit().kits().createKit(player, kitName, oneTime, autoEquip, cooldown);
        if (success) {
            MessageUtil.send(player, "kits.management.created", Map.of("name", kitName));
        } else {
            MessageUtil.send(player, "kits.management.already-exists", Map.of("name", kitName));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("aircore.command.createkit")) return List.of();
        if (args.length >= 2) {
            String current = args[args.length - 1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            List<String> currentArgs = List.of(args);
            if (!currentArgs.contains("-onetime") && "-onetime".startsWith(current)) suggestions.add("-onetime");
            if (!currentArgs.contains("-autoequip") && "-autoequip".startsWith(current)) suggestions.add("-autoequip");
            return suggestions;
        }
        return List.of();
    }
}