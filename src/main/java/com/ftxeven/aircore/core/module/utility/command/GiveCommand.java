    package com.ftxeven.aircore.core.module.utility.command;

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
        private static final String PERM_BASE = "aircore.command.give";
        private static final String PERM_OTHERS = "aircore.command.give.others";
        private static final String PERM_ALL = "aircore.command.give.all";

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
            String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

            if (!(sender instanceof Player player)) {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " <item> <player> [amount]");
                    return true;
                }
                handleGive(sender, args, selectorAll);
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
                sendError(player, label, hasExtended, "incorrect-usage");
                return true;
            }

            if (!hasExtended) {
                if (args.length > 2) {
                    sendError(player, label, false, "too-many-arguments");
                    return true;
                }
                if (args.length == 2 && !isInteger(args[1])) {
                    MessageUtil.send(player, "errors.invalid-amount", Map.of());
                    return true;
                }
            } else if (args.length > 3) {
                sendError(player, label, true, "too-many-arguments");
                return true;
            }

            Material material = Material.matchMaterial(args[0]);
            if (material == null || !material.isItem()) {
                MessageUtil.send(player, "utilities.give.invalid-item", Map.of("item", args[0]));
                return true;
            }

            String targetName = player.getName();
            String amountArg = "1";

            if (args.length == 2) {
                if (isInteger(args[1])) {
                    amountArg = args[1];
                } else {
                    targetName = args[1];
                }
            } else if (args.length == 3) {
                if (!isInteger(args[1])) {
                    targetName = args[1];
                    amountArg = args[2];
                } else {
                    amountArg = args[1];
                    targetName = args[2];
                }
            }

            if (!isInteger(amountArg)) {
                MessageUtil.send(player, "errors.invalid-amount", Map.of());
                return true;
            }

            int amount = Integer.parseInt(amountArg);

            if (!targetName.equalsIgnoreCase(player.getName())) {
                if (targetName.equalsIgnoreCase(selectorAll)) {
                    if (!hasAll) {
                        MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_ALL));
                        return true;
                    }
                } else if (!hasOthers) {
                    MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_OTHERS));
                    return true;
                }
            }

            executeGive(player, material, targetName, amount, selectorAll);
            return true;
        }

        private void executeGive(CommandSender sender, Material material, String targetName, int amount, String selectorAll) {
            ItemStack stack = new ItemStack(material, Math.max(1, amount));
            String itemName = plugin.core().itemTranslations().translate(material);
            String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

            if (targetName.equalsIgnoreCase(selectorAll)) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    giveItem(target, stack.clone());
                    if (!target.equals(sender)) {
                        MessageUtil.send(target, "utilities.give.by", Map.of("player", senderName, "amount", String.valueOf(amount), "item", itemName));
                    }
                }
                if (sender instanceof Player p) MessageUtil.send(p, "utilities.give.everyone", Map.of("amount", String.valueOf(amount), "item", itemName));
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                if (sender instanceof Player p) MessageUtil.send(p, "errors.player-not-found", Map.of());
                else sender.sendMessage("Player not found");
                return;
            }

            giveItem(target, stack);
            if (sender instanceof Player p) {
                if (target.equals(p)) MessageUtil.send(p, "utilities.give.self", Map.of("amount", String.valueOf(amount), "item", itemName));
                else {
                    MessageUtil.send(p, "utilities.give.for", Map.of("player", target.getName(), "amount", String.valueOf(amount), "item", itemName));
                    MessageUtil.send(target, "utilities.give.by", Map.of("player", p.getName(), "amount", String.valueOf(amount), "item", itemName));
                }
            } else if (plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target, "utilities.give.by", Map.of("player", senderName, "amount", String.valueOf(amount), "item", itemName));
            }
        }

        private void handleGive(CommandSender sender, String[] args, String selectorAll) {
            Material material = Material.matchMaterial(args[0]);
            if (material == null) return;
            String targetName = args[1];
            int amount = (args.length == 3 && isInteger(args[2])) ? Integer.parseInt(args[2]) : 1;
            executeGive(sender, material, targetName, amount, selectorAll);
        }

        private void giveItem(Player target, ItemStack stack) {
            plugin.scheduler().runEntityTask(target, () -> target.getInventory().addItem(stack));
        }

        private void sendError(Player player, String label, boolean hasExtended, String key) {
            String usage = plugin.commandConfig().getUsage("give", hasExtended ? "others" : null, label);
            MessageUtil.send(player, "errors." + key, Map.of("usage", usage));
        }

        private boolean isInteger(String s) {
            try { Integer.parseInt(s); return true; } catch (NumberFormatException e) { return false; }
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender,
                                          @NotNull Command cmd,
                                          @NotNull String label,
                                          String @NotNull [] args) {
            if (!(sender instanceof Player player) || !player.hasPermission(PERM_BASE)) return Collections.emptyList();
            String input = args[args.length - 1].toLowerCase();
            String selectorAll = plugin.commandConfig().getSelector("global.all", "@a");

            if (args.length == 1) return ITEM_IDS.stream().filter(id -> id.contains(input)).limit(20).toList();

            if (args.length == 2 || args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                if (player.hasPermission(PERM_OTHERS) && !isInteger(input)) {
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .limit(20)
                            .forEach(suggestions::add);
                }
                if (player.hasPermission(PERM_ALL) && selectorAll.startsWith(input)) suggestions.add(selectorAll);
                return suggestions;
            }

            return Collections.emptyList();
        }
    }