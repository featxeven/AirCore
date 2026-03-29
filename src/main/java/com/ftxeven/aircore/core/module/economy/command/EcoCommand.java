package com.ftxeven.aircore.core.module.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.module.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class EcoCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM_BASE = "aircore.command.eco";
    private static final String PERM_ALL = "aircore.command.eco.*";
    private enum Scope { SINGLE, ALL, SERVER }
    private enum Op { GIVE, TAKE, SET, RESET }

    public EcoCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            handleConsole(sender, args, label);
            return true;
        }

        if (!player.hasPermission(PERM_BASE) && !player.hasPermission(PERM_ALL)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("eco", label)));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Op op = switch (sub) {
            case "give" -> Op.GIVE;
            case "take" -> Op.TAKE;
            case "set" -> Op.SET;
            case "reset" -> Op.RESET;
            default -> null;
        };

        if (op == null) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("eco", label)));
            return true;
        }

        if (!player.hasPermission(PERM_ALL) && !player.hasPermission(PERM_BASE + "." + sub)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE + "." + sub));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("eco", sub, label)));
            return true;
        }

        if (plugin.config().errorOnExcessArgs()) {
            int max = (op == Op.RESET) ? 2 : 3;
            if (args.length > max) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.commandConfig().getUsage("eco", sub, label)));
                return true;
            }
        }

        if (op != Op.RESET && args.length < 3) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("eco", sub, label)));
            return true;
        }

        String targetArg = args[1];
        Scope scope;
        OfflinePlayer target = null;

        String selAll = plugin.commandConfig().getSelector("eco", "all");
        String selServer = plugin.commandConfig().getSelector("eco", "server");

        if (targetArg.equalsIgnoreCase(selAll)) {
            if (!player.hasPermission(PERM_ALL) && !player.hasPermission(PERM_BASE + "." + sub + ".all")) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE + "." + sub + ".all"));
                return true;
            }
            scope = Scope.ALL;
        } else if (targetArg.equalsIgnoreCase(selServer)) {
            if (!player.hasPermission(PERM_ALL) && !player.hasPermission(PERM_BASE + "." + sub + ".server")) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE + "." + sub + ".server"));
                return true;
            }
            scope = Scope.SERVER;
        } else {
            scope = Scope.SINGLE;
            target = resolve(player, targetArg);
            if (target == null) return true;
        }

        double amount = 0;
        if (op != Op.RESET) {
            Double parsed = plugin.economy().formats().parseAmount(args[2]);
            if (parsed == null) {
                MessageUtil.send(player, "errors.invalid-amount", Map.of());
                return true;
            }
            amount = parsed;
        }

        switch (op) {
            case GIVE -> handleGive(player, scope, target, targetArg, amount);
            case TAKE -> handleTake(player, scope, target, targetArg, amount);
            case SET -> handleSet(player, scope, target, targetArg, amount);
            case RESET -> handleReset(player, scope, target, targetArg);
        }

        return true;
    }

    private void handleConsole(CommandSender console, String[] args, String label) {
        String selAll = plugin.commandConfig().getSelector("eco", "all");
        String selServer = plugin.commandConfig().getSelector("eco", "server");

        if (args.length < 2) {
            console.sendMessage("Usage: /" + label + " <give|set|take|reset> <player|" + selAll + "|" + selServer + "> [amount]");
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Op op = switch (sub) {
            case "give" -> Op.GIVE;
            case "take" -> Op.TAKE;
            case "set" -> Op.SET;
            case "reset" -> Op.RESET;
            default -> null;
        };

        if (op == null) {
            console.sendMessage("Usage: /" + label + " <give|set|take|reset> <player|" + selAll + "|" + selServer + "> [amount]");
            return;
        }

        String targetArg = args[1];
        Scope scope;
        OfflinePlayer target = null;

        if (targetArg.equalsIgnoreCase(selAll)) scope = Scope.ALL;
        else if (targetArg.equalsIgnoreCase(selServer)) scope = Scope.SERVER;
        else {
            scope = Scope.SINGLE;
            target = resolve(null, targetArg);
            if (target == null) {
                console.sendMessage("Player not found");
                return;
            }
        }

        double amount = 0;
        if (op != Op.RESET) {
            if (args.length < 3) {
                console.sendMessage("Usage: /" + label + " " + sub + " <player|" + selAll + "|" + selServer + "> <amount>");
                return;
            }
            Double parsed = plugin.economy().formats().parseAmount(args[2]);
            if (parsed == null) {
                console.sendMessage("Invalid amount.");
                return;
            }
            amount = parsed;
        }

        switch (op) {
            case GIVE -> handleGive(null, scope, target, targetArg, amount);
            case TAKE -> handleTake(null, scope, target, targetArg, amount);
            case SET -> handleSet(null, scope, target, targetArg, amount);
            case RESET -> handleReset(null, scope, target, targetArg);
        }
    }

    private void handleGive(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (scope == Scope.SINGLE) {
            EconomyManager.Result res = plugin.economy().transactions().deposit(target.getUniqueId(), amount);
            String targetName = plugin.database().records().getRealName(targetArg);
            if (res.type() == EconomyManager.ResultType.MAX_LIMIT) {
                sendToSender(sender, "economy.give.error-exceed",
                        "Gave would exceed max limit for " + targetName,
                        Map.of("player", consoleName(sender), "amount", plugin.economy().formats().formatAmount(plugin.config().economyMaxBalance())));
                return;
            }

            if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                handleResult(sender, target, res, "economy.give.by", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
            }

            if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.give.player", Map.of("player", targetName, "amount", plugin.economy().formats().formatAmount(amount)));
            } else if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.give.self", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
            } else if (sender == null) {
                sendToSender(null, "economy.give.player",
                        "Gave " + plugin.economy().formats().formatAmount(amount) + " to " + targetName,
                        Map.of("player", targetName, "amount", plugin.economy().formats().formatAmount(amount)));
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            boolean isServerScope = (scope == Scope.SERVER);
            if (amount <= 0) {
                sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
                return;
            }
            double max = plugin.config().economyMaxBalance();
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.error-max", "Amount exceeds max.", Map.of("amount", plugin.economy().formats().formatAmount(max)));
                return;
            }

            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);
            Collection<UUID> targets = isServerScope ? plugin.database().records().getAllKnownUuids() :
                    Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();

            for (UUID uuid : targets) {
                double current = plugin.economy().balances().getBalance(uuid);
                double allowed = (max >= 0) ? Math.max(0, max - current) : amount;
                double given = Math.min(amount, allowed);
                if (given <= 0) continue;

                plugin.economy().transactions().deposit(uuid, given);

                if (shouldNotify && (sender == null || !uuid.equals(sender.getUniqueId()))) {
                    notifyPlayer(uuid, "economy.give.by", Map.of("player", actor, "amount", plugin.economy().formats().formatAmount(given)));
                }
            }
            sendToSender(sender, isServerScope ? "economy.give.all" : "economy.give.online", "Gave balances.", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
        });
    }

    private void handleTake(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (amount <= 0) {
            sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
            return;
        }

        double min = plugin.config().economyMinBalance();

        if (scope == Scope.SINGLE) {
            double current = plugin.economy().balances().getBalance(target.getUniqueId());
            double allowed = (min != -1) ? current - min : amount;
            String targetName = plugin.database().records().getRealName(targetArg);

            if (allowed < amount) {
                sendToSender(sender, "economy.take.error-insufficient", "Insufficient funds.", Map.of("player", targetName));
                return;
            }

            EconomyManager.Result res = plugin.economy().transactions().withdraw(target.getUniqueId(), amount);
            if (res.type() == EconomyManager.ResultType.SUCCESS) {
                double actualTaken = plugin.economy().formats().round(current - res.balance());
                if (target.isOnline() && (sender != null || plugin.config().consoleToPlayerFeedback())) {
                    String actor = consoleName(sender);
                    if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                        notifyPlayer(target.getUniqueId(), "economy.take.by", Map.of("player", actor, "amount", plugin.economy().formats().formatAmount(actualTaken)));
                    }
                }
                if (sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtil.send(sender, "economy.take.player", Map.of("player", targetName, "amount", plugin.economy().formats().formatAmount(actualTaken)));
                } else if (sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtil.send(sender, "economy.take.self", Map.of("amount", plugin.economy().formats().formatAmount(actualTaken)));
                } else if (sender == null) {
                    sendToSender(null, "economy.take.player", "Took from " + targetName, Map.of("player", targetName, "amount", plugin.economy().formats().formatAmount(actualTaken)));
                }
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            boolean isServerScope = (scope == Scope.SERVER);
            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);
            Collection<UUID> targets = isServerScope ? plugin.database().records().getAllKnownUuids() :
                    Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();

            for (UUID uuid : targets) {
                double current = plugin.economy().balances().getBalance(uuid);
                double allowed = (min != -1) ? current - min : amount;
                double taken = Math.min(amount, allowed);
                if (taken <= 0) continue;

                EconomyManager.Result res = plugin.economy().transactions().withdraw(uuid, taken);
                if (res.type() == EconomyManager.ResultType.SUCCESS && shouldNotify && (sender == null || !uuid.equals(sender.getUniqueId()))) {
                    double actualTaken = plugin.economy().formats().round(current - res.balance());
                    notifyPlayer(uuid, "economy.take.by", Map.of("player", actor, "amount", plugin.economy().formats().formatAmount(actualTaken)));
                }
            }
            sendToSender(sender, isServerScope ? "economy.take.all" : "economy.take.online", "Took balances.", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
        });
    }

    private void handleSet(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (scope == Scope.SINGLE) {
            double min = plugin.config().economyMinBalance();
            double max = plugin.config().economyMaxBalance();

            if (min != -1 && amount < min) {
                sendToSender(sender, "economy.error-min", "Hit min limit.", Map.of("amount", plugin.economy().formats().formatAmount(min)));
                return;
            }
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.error-max", "Hit max limit.", Map.of("amount", plugin.economy().formats().formatAmount(max)));
                return;
            }

            EconomyManager.Result res = plugin.economy().transactions().setBalance(target.getUniqueId(), amount);
            if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                handleResult(sender, target, res, "economy.set.by", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
            }
            if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null) {
                if (!sender.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtil.send(sender, "economy.set.player", Map.of("player", plugin.database().records().getRealName(targetArg), "amount", plugin.economy().formats().formatAmount(amount)));
                } else {
                    MessageUtil.send(sender, "economy.set.self", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
                }
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            boolean isServerScope = (scope == Scope.SERVER);
            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);
            Collection<UUID> targets = isServerScope ? plugin.database().records().getAllKnownUuids() :
                    Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();

            for (UUID uuid : targets) {
                EconomyManager.Result res = plugin.economy().transactions().setBalance(uuid, amount);
                if (res.type() == EconomyManager.ResultType.SUCCESS && shouldNotify && (sender == null || !uuid.equals(sender.getUniqueId()))) {
                    notifyPlayer(uuid, "economy.set.by", Map.of("player", actor, "amount", plugin.economy().formats().formatAmount(amount)));
                }
            }
            sendToSender(sender, isServerScope ? "economy.set.all" : "economy.set.online", "Set balances.", Map.of("amount", plugin.economy().formats().formatAmount(amount)));
        });
    }

    private void handleReset(Player sender, Scope scope, OfflinePlayer target, String targetArg) {
        if (scope == Scope.SINGLE) {
            plugin.economy().transactions().resetBalance(target.getUniqueId());
            if (target.isOnline() && (sender != null || plugin.config().consoleToPlayerFeedback())) {
                String actor = consoleName(sender);
                if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                    notifyPlayer(target.getUniqueId(), "economy.reset.by", Map.of("player", actor));
                }
            }
            if (sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.reset.player", Map.of("player", plugin.database().records().getRealName(targetArg)));
            } else if (sender != null) {
                MessageUtil.send(sender, "economy.reset.self", Map.of());
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            boolean isServerScope = (scope == Scope.SERVER);
            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);
            Collection<UUID> targets = isServerScope ? plugin.database().records().getAllKnownUuids() :
                    Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();

            for (UUID uuid : targets) {
                plugin.economy().transactions().resetBalance(uuid);
                if (shouldNotify && (sender == null || !uuid.equals(sender.getUniqueId()))) {
                    notifyPlayer(uuid, "economy.reset.by", Map.of("player", actor));
                }
            }
            sendToSender(sender, isServerScope ? "economy.reset.all" : "economy.reset.online", "Reset balances.", Map.of());
        });
    }

    private String consoleName(Player sender) {
        return (sender != null) ? sender.getName() : String.valueOf(plugin.lang().get("general.console-name"));
    }

    private void sendToSender(Player sender, String key, String fallback, Map<String, String> placeholders) {
        if (sender == null) Bukkit.getConsoleSender().sendMessage(fallback);
        else MessageUtil.send(sender, key, placeholders);
    }

    private void notifyPlayer(UUID uuid, String key, Map<String, String> placeholders) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        plugin.scheduler().runTask(() -> MessageUtil.send(p, key, placeholders));
    }

    private void handleResult(Player sender, OfflinePlayer target, EconomyManager.Result res, String notifyKey, Map<String, String> placeholders) {
        switch (res.type()) {
            case SUCCESS -> {
                if (target.isOnline() && (sender != null || plugin.config().consoleToPlayerFeedback())) {
                    Map<String, String> merged = new HashMap<>(placeholders);
                    merged.putIfAbsent("player", consoleName(sender));
                    MessageUtil.send(target.getPlayer(), notifyKey, merged);
                }
            }
            case MAX_LIMIT -> sendToSender(sender, "economy.error-max", "Hit max limit.", Map.of("amount", plugin.economy().formats().formatAmount(plugin.config().economyMaxBalance())));
            case MIN_LIMIT -> sendToSender(sender, "economy.error-min", "Hit min limit.", Map.of("amount", plugin.economy().formats().formatAmount(plugin.config().economyMinBalance())));
            case INVALID -> sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
            default -> {}
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission(PERM_BASE) && !sender.hasPermission(PERM_ALL)) return completions;

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("give", "take", "set", "reset")) {
                if (sender.hasPermission(PERM_ALL) || sender.hasPermission(PERM_BASE + "." + sub)) {
                    if (sub.startsWith(input)) completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String input = args[1].toLowerCase(Locale.ROOT);

            String selAll = plugin.commandConfig().getSelector("eco", "all");
            String selServer = plugin.commandConfig().getSelector("eco", "server");

            if (sender.hasPermission(PERM_ALL) || sender.hasPermission(PERM_BASE + "." + sub + ".all")) {
                if (selAll.startsWith(input)) completions.add(selAll);
            }
            if (sender.hasPermission(PERM_ALL) || sender.hasPermission(PERM_BASE + "." + sub + ".server")) {
                if (selServer.startsWith(input)) completions.add(selServer);
            }

            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .limit(20)
                    .forEach(completions::add);
        }
        return completions;
    }
}