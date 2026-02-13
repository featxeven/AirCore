package com.ftxeven.aircore.core.utility.command;

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

        if (!(sender instanceof Player player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + label + " <item> [amount] [player|@a]");
                return true;
            }
            handleGive(sender, args);
            return true;
        }

        if (!player.hasPermission("aircore.command.give")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.give"));
            return true;
        }

        boolean hasOthers = player.hasPermission("aircore.command.give.others");
        boolean hasAll = player.hasPermission("aircore.command.give.all");

        if (args.length == 0) {
            String usage = (hasOthers || hasAll)
                    ? plugin.config().getUsage("give", "others", label)
                    : plugin.config().getUsage("give", label);
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", usage));
            return true;
        }

        if (plugin.config().errorOnExcessArgs()) {
            boolean isTooMany = false;
            if (!hasOthers && !hasAll && args.length > 2) isTooMany = true;
            else if (args.length > 3) isTooMany = true;

            if (isTooMany) {
                String usage = (hasOthers || hasAll)
                        ? plugin.config().getUsage("give", "others", label)
                        : plugin.config().getUsage("give", label);
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
                return true;
            }
        }

        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem()) {
            MessageUtil.send(player, "utilities.give.invalid-item", Map.of("item", args[0]));
            return true;
        }

        if (args.length >= 2) {
            String arg1 = args[1];
            boolean arg1IsInt = isInteger(arg1);
            boolean arg1IsAllSelector = arg1.equalsIgnoreCase("@a");

            if (!arg1IsInt) {
                if (arg1IsAllSelector) {
                    if (!hasAll) {
                        MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.give.all"));
                        return true;
                    }
                } else if (!hasOthers) {
                    MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.give.others"));
                    return true;
                }
            }
        }

        if (args.length == 3) {
            String arg2 = args[2];
            boolean arg2IsInt = isInteger(arg2);
            boolean arg2IsAllSelector = arg2.equalsIgnoreCase("@a");

            if (!arg2IsInt) {
                if (arg2IsAllSelector) {
                    if (!hasAll) {
                        MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.give.all"));
                        return true;
                    }
                } else if (!hasOthers) {
                    MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.give.others"));
                    return true;
                }
            }
        }

        handleGive(player, args);
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        Material material = Material.matchMaterial(args[0]);
        int amount = 1;
        String targetArg = null;

        if (args.length == 2) {
            if (isInteger(args[1])) {
                amount = Integer.parseInt(args[1]);
            } else {
                targetArg = args[1];
            }
        } else if (args.length == 3) {
            if (isInteger(args[1])) {
                amount = Integer.parseInt(args[1]);
                targetArg = args[2];
            } else if (isInteger(args[2])) {
                targetArg = args[1];
                amount = Integer.parseInt(args[2]);
            } else {
                if (sender instanceof Player p) {
                    MessageUtil.send(p, "errors.invalid-amount", Map.of());
                } else {
                    sender.sendMessage("Invalid amount.");
                }
                return;
            }
        }

        assert material != null;
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        String itemName = plugin.core().itemTranslations().translate(material);
        String senderName = (sender instanceof Player p) ? p.getName() : plugin.lang().get("general.console-name");

        if (targetArg == null) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Console must specify a player or @a.");
                return;
            }
            giveItem(p, stack);
            MessageUtil.send(p, "utilities.give.self", Map.of("amount", String.valueOf(amount), "item", itemName));
            return;
        }

        if (targetArg.equalsIgnoreCase("@a")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                giveItem(target, stack.clone());
                if (!target.equals(sender)) {
                    MessageUtil.send(target, "utilities.give.by", Map.of("player", senderName, "amount", String.valueOf(amount), "item", itemName));
                }
            }

            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.give.everyone", Map.of("amount", String.valueOf(amount), "item", itemName));
            } else {
                sender.sendMessage("Gave " + amount + " " + itemName + " to everyone.");
            }
            return;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "errors.player-not-found", Map.of("player", targetArg));
            } else {
                sender.sendMessage("Player not found.");
            }
            return;
        }

        giveItem(target, stack);

        if (sender instanceof Player p) {
            if (target.equals(p)) {
                MessageUtil.send(p, "utilities.give.self", Map.of("amount", String.valueOf(amount), "item", itemName));
            } else {
                MessageUtil.send(p, "utilities.give.for", Map.of("player", target.getName(), "amount", String.valueOf(amount), "item", itemName));
                MessageUtil.send(target, "utilities.give.by", Map.of("player", p.getName(), "amount", String.valueOf(amount), "item", itemName));
            }
        } else {
            sender.sendMessage("Gave " + amount + " " + itemName + " to " + target.getName());
            if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.give.by", Map.of("player", senderName, "amount", String.valueOf(amount), "item", itemName));
            }
        }
    }

    private void giveItem(Player target, ItemStack stack) {
        plugin.scheduler().runEntityTask(target, () -> target.getInventory().addItem(stack));
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        String input = args[args.length - 1].toLowerCase();

        if (sender instanceof Player player) {
            if (!player.hasPermission("aircore.command.give")) return Collections.emptyList();

            if (args.length == 1) {
                return ITEM_IDS.stream().filter(id -> id.contains(input)).toList();
            }

            if (args.length == 2 || args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                if (player.hasPermission("aircore.command.give.others")) {
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .limit(20)
                            .forEach(suggestions::add);
                }
                if (player.hasPermission("aircore.command.give.all") && "@a".startsWith(input)) {
                    suggestions.add("@a");
                }
                return suggestions;
            }
        } else {
            if (args.length == 1) {
                return ITEM_IDS.stream().filter(id -> id.contains(input)).toList();
            }

            if (args.length == 2 || args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                if ("@a".startsWith(input)) suggestions.add("@a");
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .limit(20)
                        .forEach(suggestions::add);
                return suggestions;
            }
        }

        return Collections.emptyList();
    }
}