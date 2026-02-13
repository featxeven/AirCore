package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class FlyCommand implements TabExecutor {

    private final AirCore plugin;

    public FlyCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            String consoleName = plugin.lang().get("general.console-name");

            if (args.length != 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            toggleFlyAsync(target, null, consoleName, false, sender);
            return true;
        }

        if (!player.hasPermission("aircore.command.fly")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.fly"));
            return true;
        }

        if (args.length == 0) {
            toggleFlyAsync(player, player, player.getName(), true, null);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target != null && target.equals(player)) {
            toggleFlyAsync(player, player, player.getName(), true, null);
            return true;
        }

        if (!player.hasPermission("aircore.command.fly.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.fly.others"));
            return true;
        }

        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found", Map.of("player", args[0]));
            return true;
        }

        toggleFlyAsync(target, player, player.getName(), false, null);
        return true;
    }

    private void toggleFlyAsync(Player target, Player sender, String senderName,
                                boolean selfToggle, CommandSender console) {
        plugin.scheduler().runEntityTask(target, () -> {
            boolean newState = !target.getAllowFlight();
            target.setAllowFlight(newState);
            if (!newState && target.isFlying()) {
                target.setFlying(false);
            }

            plugin.core().toggles().set(target.getUniqueId(), ToggleService.Toggle.FLY, newState);

            if (selfToggle) {
                MessageUtil.send(target, newState ? "utilities.fly.enabled" : "utilities.fly.disabled", Map.of());
            } else {
                if (sender != null) {
                    // Player sender
                    MessageUtil.send(sender,
                            newState ? "utilities.fly.enabled-for" : "utilities.fly.disabled-for",
                            Map.of("player", target.getName()));
                    MessageUtil.send(target,
                            newState ? "utilities.fly.enabled-by" : "utilities.fly.disabled-by",
                            Map.of("player", senderName));
                } else if (console != null) {
                    console.sendMessage("Fly for " + target.getName() + " -> "
                            + (newState ? "enabled" : "disabled"));
                    if (plugin.config().consoleToPlayerFeedback()) {
                        MessageUtil.send(target,
                                newState ? "utilities.fly.enabled-by" : "utilities.fly.disabled-by",
                                Map.of("player", senderName));
                    }
                }
            }
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.fly.others")) {
                return List.of();
            }
        }

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .limit(20)
                .toList();
    }
}
