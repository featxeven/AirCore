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
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.clearinventory")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.clearinventory"));
            return true;
        }

        if (args.length == 0) {
            clearInventory(player);
            MessageUtil.send(player, "utilities.inventory.cleared", Map.of());
            return true;
        }

        if (args[0].equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.clearinventory.all")) {
                MessageUtil.send(player, "errors.player-not-found", Map.of("player", "@a"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                clearInventory(target);
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.inventory.cleared-by",
                            Map.of("player", player.getName()));
                }
            }
            MessageUtil.send(player, "utilities.inventory.cleared-everyone", Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.clearinventory.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.clearinventory.others"));
            return true;
        }

        OfflinePlayer resolved = resolve(player, args[0]);
        if (resolved == null) return true;

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player targetOnline = resolved.getPlayer();
            clearInventory(targetOnline);

            if (targetOnline.equals(player)) {
                MessageUtil.send(player, "utilities.inventory.cleared", Map.of());
            } else {
                MessageUtil.send(player, "utilities.inventory.cleared-for", Map.of("player", targetOnline.getName()));
                MessageUtil.send(targetOnline, "utilities.inventory.cleared-by", Map.of("player", player.getName()));
            }
        } else {
            clearOfflineInventory(resolved.getUniqueId());
            MessageUtil.send(player, "utilities.inventory.cleared-for",
                    Map.of("player", resolved.getName() != null ? resolved.getName() : args[0]));
        }

        return true;
    }

    private void clearInventory(Player target) {
        plugin.scheduler().runEntityTask(target, () -> {
            target.getInventory().clear();

            ItemStack[] emptyContents = new ItemStack[36];
            ItemStack[] emptyArmor = new ItemStack[4];

            plugin.database().inventories().saveInventory(
                    target.getUniqueId(),
                    emptyContents,
                    emptyArmor,
                    null
            );
        });
    }

    private void clearOfflineInventory(UUID uuid) {
        ItemStack[] emptyContents = new ItemStack[36];
        ItemStack[] emptyArmor = new ItemStack[4];

        plugin.database().inventories().saveInventory(uuid, emptyContents, emptyArmor, null);

        plugin.database().inventories().saveEnderchest(uuid, new ItemStack[27]);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase();

        List<String> suggestions = new ArrayList<>();
        if (sender.hasPermission("aircore.command.clearinventory.others")) {
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(20)
                    .toList());
        }

        if (sender.hasPermission("aircore.command.clearinventory.all") && "@a".startsWith(input)) {
            suggestions.add("@a");
        }

        return suggestions;
    }

    private OfflinePlayer resolve(Player sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        UUID cached = plugin.getNameCache().get(name.toLowerCase());
        if (cached != null) return Bukkit.getOfflinePlayer(cached);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}