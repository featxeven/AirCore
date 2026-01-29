package com.ftxeven.aircore.module.core.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GiveCommand implements TabExecutor {

    private final AirCore plugin;
    private static final List<String> ITEM_IDS = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .map(mat -> mat.getKey().toString())
            .toList();

    public GiveCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        // Console execution
        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <item> [amount] [player|@a]");
                return true;
            }

            Material material = Material.matchMaterial(args[0]);
            if (material == null || !material.isItem()) {
                sender.sendMessage("Invalid item: " + args[0]);
                return true;
            }

            int amount = 1;
            String targetArg = null;

            if (args.length == 2) {
                if (isInteger(args[1])) {
                    amount = Integer.parseInt(args[1]);
                } else {
                    targetArg = args[1];
                }
            } else {
                if (!isInteger(args[1])) {
                    sender.sendMessage("Invalid amount: " + args[1]);
                    return true;
                }
                amount = Integer.parseInt(args[1]);
                targetArg = args[2];
            }

            ItemStack stack = new ItemStack(material, amount);
            String itemName = getItemName(material);

            if (targetArg == null) {
                sender.sendMessage("Console must specify a player or @a.");
                return true;
            }

            if (targetArg.equalsIgnoreCase("@a")) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    giveItem(target, stack.clone());
                    if (plugin.config().consoleToPlayerFeedback()) {
                        MessageUtil.send(target, "utilities.give.by",
                                Map.of("player", plugin.lang().get("general.console-name"),
                                        "amount", String.valueOf(amount),
                                        "item", itemName));
                    }
                }
                sender.sendMessage("Gave " + amount + " " + itemName + " to everyone.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetArg);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }

            giveItem(target, stack);
            sender.sendMessage("Gave " + amount + " " + itemName + " to " + target.getName());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.give.by",
                        Map.of("player", plugin.lang().get("general.console-name"),
                                "amount", String.valueOf(amount),
                                "item", itemName));
            }
            return true;
        }

        // Player execution
        if (!player.hasPermission("aircore.command.give")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.give"));
            return true;
        }

        if (args.length == 0) {
            if (player.hasPermission("aircore.command.give.others")) {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("give", "others", label)));
            } else {
                MessageUtil.send(player, "errors.incorrect-usage",
                        Map.of("usage", plugin.config().getUsage("give", label)));
            }
            return true;
        }

        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem()) {
            MessageUtil.send(player, "utilities.give.invalid-item", Map.of("item", args[0]));
            return true;
        }

        int amount = 1;
        String targetArg = null;

        if (args.length == 2) {
            if (isInteger(args[1])) {
                amount = Integer.parseInt(args[1]);
            } else {
                targetArg = args[1];
            }
        } else if (args.length == 3) {
            if (!isInteger(args[1])) {
                MessageUtil.send(player, "utilities.give.invalid-amount", Map.of());
                return true;
            }
            amount = Integer.parseInt(args[1]);
            targetArg = args[2];
        }

        ItemStack stack = new ItemStack(material, amount);
        String itemName = getItemName(material);

        // Self give
        if (targetArg == null) {
            giveItem(player, stack);
            MessageUtil.send(player, "utilities.give.self",
                    Map.of("amount", String.valueOf(amount),
                            "item", itemName));
            return true;
        }

        // Give to all
        if (targetArg.equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.give.all")) {
                MessageUtil.send(player, "errors.no-permission",
                        Map.of("permission", "aircore.command.give.all"));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                giveItem(target, stack.clone());
                if (!target.equals(player)) {
                    MessageUtil.send(target, "utilities.give.by",
                            Map.of("player", player.getName(),
                                    "amount", String.valueOf(amount),
                                    "item", itemName));
                }
            }
            MessageUtil.send(player, "utilities.give.everyone",
                    Map.of("amount", String.valueOf(amount),
                            "item", itemName));
            return true;
        }

        // Give to specific player
        if (!player.hasPermission("aircore.command.give.others")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.give.others"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            MessageUtil.send(player, "errors.player-not-found",
                    Map.of("player", targetArg));
            return true;
        }

        // If target is the same as sender, treat as self
        if (target.equals(player)) {
            giveItem(player, stack);
            MessageUtil.send(player, "utilities.give.self",
                    Map.of("amount", String.valueOf(amount),
                            "item", itemName));
            return true;
        }

        giveItem(target, stack);
        MessageUtil.send(player, "utilities.give.for",
                Map.of("player", target.getName(),
                        "amount", String.valueOf(amount),
                        "item", itemName));
        MessageUtil.send(target, "utilities.give.by",
                Map.of("player", player.getName(),
                        "amount", String.valueOf(amount),
                        "item", itemName));
        return true;
    }

    private void giveItem(Player target, ItemStack stack) {
        plugin.scheduler().runEntityTask(target, () ->
                target.getInventory().addItem(stack)
        );
    }

    private String getItemName(Material material) {
        return plugin.core().itemTranslations().translate(material);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("aircore.command.give")) {
                    return List.of();
                }
            }

            String input = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String id : ITEM_IDS) {
                if (id.contains(input)) {
                    matches.add(id);
                }
            }
            return matches;
        }

        if (args.length == 2 || args.length == 3) {
            String input = args[args.length - 1].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            if (!(sender instanceof Player) || sender.hasPermission("aircore.command.give.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String name = p.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        suggestions.add(name);
                    }
                }
            }

            if (!(sender instanceof Player) || sender.hasPermission("aircore.command.give.all")) {
                if ("@a".startsWith(input)) {
                    suggestions.add("@a");
                }
            }

            return suggestions;
        }

        return List.of();
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}