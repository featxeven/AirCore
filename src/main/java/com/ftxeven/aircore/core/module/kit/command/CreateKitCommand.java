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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CreateKitCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERMISSION = "aircore.command.createkit";

    public CreateKitCommand(AirCore plugin) {
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

        String usage = plugin.commandConfig().getUsage("createkit", label);

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        String kitName = args[0].toLowerCase();

        if (plugin.kit().kits().exists(kitName)) {
            MessageUtil.send(player, "kits.management.already-exists", Map.of("name", kitName));
            return true;
        }

        boolean oneTime = false;
        boolean autoEquip = false;
        long cooldown = 0L;

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
                MessageUtil.send(player, "errors.invalid-format", Map.of("input", arg));
                return true;
            }
        }

        boolean success = plugin.kit().kits().createKit(player, kitName, oneTime, autoEquip, cooldown);

        if (success) {
            MessageUtil.send(player, "kits.management.created", Map.of("name", kitName));
        } else {
            MessageUtil.send(player, "kits.management.error-creating", Map.of("name", kitName));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length <= 1) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();
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