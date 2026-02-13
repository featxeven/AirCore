package com.ftxeven.aircore.core.home.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.home.HomeManager;
import com.ftxeven.aircore.core.home.HomeService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class SetHomeCommand implements TabExecutor {

    private final AirCore plugin;
    private final HomeManager manager;

    public SetHomeCommand(AirCore plugin, HomeManager manager) {
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

        if (!player.hasPermission("aircore.command.sethome")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.sethome"));
            return true;
        }

        if (plugin.config().homesAllowNames() && args.length == 0) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("sethome", label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("sethome", label)));
            return true;
        }

        String nameArg = args.length > 0 ? args[0].toLowerCase() : "";

        HomeService.Result result = manager.homes().setHome(player, nameArg);

        switch (result.status()) {
            case SUCCESS -> MessageUtil.send(player, "homes.management.created",
                    Map.of("name", result.homeName()));
            case INVALID_NAME -> MessageUtil.send(player, "homes.validation.invalid-name", Map.of());
            case NAME_TOO_LONG -> MessageUtil.send(player, "homes.validation.name-too-long",
                    Map.of("max", String.valueOf(plugin.config().homesMaxNameLength())));
            case DISABLED_WORLD -> MessageUtil.send(player, "homes.validation.cannot-set", Map.of());
            case ALREADY_EXISTS -> MessageUtil.send(player, "homes.management.already-exists",
                    Map.of("name", result.homeName()));
            case LIMIT_REACHED -> MessageUtil.send(player, "homes.validation.limit-reached",
                    Map.of("limit", String.valueOf(manager.homes().getLimit(player.getUniqueId()))));
            default -> {}
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return List.of();
    }
}