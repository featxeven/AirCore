package com.ftxeven.aircore.core.module.utility.command;

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
    private static final String PERM_BASE = "aircore.command.clearinventory";
    private static final String PERM_OTHERS = "aircore.command.clearinventory.others";
    private static final String PERM_ALL = "aircore.command.clearinventory.all";

    public ClearInventoryCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

        if (!(sender instanceof Player player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /" + label + " <player|" + selectorAll + ">");
                return true;
            }
            handleClear(sender, args[0], selectorAll);
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);
        boolean hasAll = player.hasPermission(PERM_ALL);
        boolean hasExtended = hasOthers || hasAll;

        if (args.length == 0) {
            handleClear(player, player.getName(), selectorAll);
            return true;
        }

        if (!hasExtended) {
            sendError(player, label, false);
            return true;
        }

        if (args.length > 1) {
            sendError(player, label, true);
            return true;
        }

        String targetArg = args[0];

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            if (!hasAll) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                return true;
            }
        } else {
            if (!hasOthers) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_OTHERS));
                return true;
            }
        }

        handleClear(player, targetArg, selectorAll);
        return true;
    }

    private void handleClear(CommandSender sender, String targetArg, String selectorAll) {
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));
        boolean feedbackEnabled = plugin.config().consoleToPlayerFeedback();

        if (targetArg.equalsIgnoreCase(selectorAll)) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                performClearOnline(target);
                if (!target.equals(sender)) {
                    MessageUtil.send(target, "utilities.inventory.cleared-by", Map.of("player", senderName));
                }
            }
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.inventory.cleared-everyone", Map.of());
            else sender.sendMessage("Cleared inventory for all online players.");
            return;
        }

        OfflinePlayer resolved = resolve(sender, targetArg);
        if (resolved == null) return;

        String displayName = resolved.getName() != null ? resolved.getName() : targetArg;

        if (resolved.isOnline() && resolved.getPlayer() != null) {
            Player targetOnline = resolved.getPlayer();
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
                if (feedbackEnabled) {
                    MessageUtil.send(targetOnline, "utilities.inventory.cleared-by", Map.of("player", senderName));
                }
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
    }

    private void sendError(Player player, String label, boolean hasOthers) {
        String usage = plugin.commandConfig().getUsage("clearinventory", hasOthers ? "others" : null, label);
        MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.player-never-joined", Map.of());
        } else {
            sender.sendMessage("Player not found");
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) return Collections.emptyList();

        String input = args[0].toLowerCase();
        String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");
        List<String> suggestions = new ArrayList<>();

        if (sender instanceof Player player) {
            if (!player.hasPermission(PERM_BASE)) return Collections.emptyList();

            if (player.hasPermission(PERM_OTHERS)) {
                Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(input)).limit(20).forEach(suggestions::add);
            }
            if (player.hasPermission(PERM_ALL) && selectorAll.toLowerCase().startsWith(input)) {
                suggestions.add(selectorAll);
            }
        } else {
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input)).limit(20).forEach(suggestions::add);
            if (selectorAll.toLowerCase().startsWith(input)) suggestions.add(selectorAll);
        }
        return suggestions;
    }
}