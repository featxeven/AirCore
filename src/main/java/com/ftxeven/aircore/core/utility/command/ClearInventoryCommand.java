package com.ftxeven.aircore.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClearInventoryCommand implements TabExecutor {

    private final AirCore plugin;

    public ClearInventoryCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            handleClear(sender, args[0]);
            return true;
        }

        if (!player.hasPermission("aircore.command.clearinventory")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.clearinventory"));
            return true;
        }

        if (args.length == 0) {
            handleClear(player, player.getName());
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.clearinventory.others");
        boolean hasAll = player.hasPermission("aircore.command.clearinventory.all");

        if (!hasOthers && !hasAll) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.clearinventory.others"));
            return true;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            String usage = hasOthers
                    ? plugin.config().getUsage("clearinventory", "others", label)
                    : plugin.config().getUsage("clearinventory", label);

            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase("@a")) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.clearinventory.all"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                performClearOnline(target);
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.inventory.cleared-by", Map.of("player", player.getName()));
                }
            }
            MessageUtil.send(player, "utilities.inventory.cleared-everyone", Map.of());
            return true;
        }

        if (!hasOthers) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.clearinventory.others"));
            return true;
        }

        handleClear(player, targetName);
        return true;
    }

    private void handleClear(CommandSender sender, String targetName) {
        OfflinePlayer resolved = resolve(sender, targetName);
        if (resolved == null) return;

        String displayName = resolved.getName() != null ? resolved.getName() : targetName;

        if (resolved instanceof Player targetOnline) {
            performClearOnline(targetOnline);
            if (sender instanceof Player p) {
                if (targetOnline.equals(p)) {
                    MessageUtil.send(p, "utilities.inventory.cleared", Map.of());
                } else {
                    MessageUtil.send(p, "utilities.inventory.cleared-for", Map.of("player", displayName));
                    MessageUtil.send(targetOnline, "utilities.inventory.cleared-by", Map.of("player", p.getName()));
                }
            } else {
                sender.sendMessage("Cleared inventory for " + displayName);
            }
        } else {
            final UUID targetId = resolved.getUniqueId();
            plugin.scheduler().runAsync(() -> {
                performClearOffline(targetId);
                plugin.scheduler().runTask(() -> {
                    if (sender instanceof Player p) {
                        MessageUtil.send(p, "utilities.inventory.cleared-for", Map.of("player", displayName));
                    } else {
                        sender.sendMessage("Cleared offline inventory for " + displayName);
                    }
                });
            });
        }
    }

    private void performClearOnline(Player target) {
        plugin.scheduler().runEntityTask(target, () -> {
            target.getInventory().clear();
            plugin.database().inventories().saveInventory(target.getUniqueId(), new ItemStack[36], new ItemStack[4], null);
        });
    }

    private void performClearOffline(UUID uuid) {
        plugin.database().inventories().saveInventory(uuid, new ItemStack[36], new ItemStack[4], null);
        plugin.database().inventories().saveEnderchest(uuid, new ItemStack[27]);
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player never joined.");
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !(sender instanceof Player player)) return Collections.emptyList();

        String input = args[0].toLowerCase();
        List<String> suggestions = new ArrayList<>();

        if (player.hasPermission("aircore.command.clearinventory.others")) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(input)) suggestions.add(p.getName());
            });
        }

        if (player.hasPermission("aircore.command.clearinventory.all") && "@a".startsWith(input)) {
            suggestions.add("@a");
        }

        return suggestions;
    }
}