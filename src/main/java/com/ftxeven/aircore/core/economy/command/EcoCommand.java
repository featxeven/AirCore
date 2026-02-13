package com.ftxeven.aircore.core.economy.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EcoCommand implements TabExecutor {

    private final AirCore plugin;
    private final EconomyManager manager;

    public EcoCommand(AirCore plugin, EconomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private enum Scope {SINGLE, ONLINE, ALL}

    private enum Op {GIVE, TAKE, SET, RESET}

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            handleConsole(sender, args, label);
            return true;
        }

        if (!player.hasPermission("aircore.command.eco") && !player.hasPermission("aircore.command.eco.*")) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.eco"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("eco", label)));
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
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("eco", label)));
            return true;
        }

        if (!player.hasPermission("aircore.command.eco.*") && !player.hasPermission("aircore.command.eco." + sub)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.eco." + sub));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("eco", sub, label)));
            return true;
        }

        String targetArg = args[1];
        Scope scope;
        OfflinePlayer target = null;

        if (targetArg.equalsIgnoreCase("@o")) {
            if (!player.hasPermission("aircore.command.eco.*") && !player.hasPermission("aircore.command.eco." + sub + ".online")) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.eco." + sub + ".online"));
                return true;
            }
            scope = Scope.ONLINE;
        } else if (targetArg.equalsIgnoreCase("@a")) {
            if (!player.hasPermission("aircore.command.eco.*") && !player.hasPermission("aircore.command.eco." + sub + ".all")) {
                MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.eco." + sub + ".all"));
                return true;
            }
            scope = Scope.ALL;
        } else {
            scope = Scope.SINGLE;
            target = resolve(player, targetArg);
            if (target == null) return true;
        }

        double amount = 0;
        if (op != Op.RESET) {
            if (args.length < 3) {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("eco", sub, label)));
                return true;
            }
            Double parsed = manager.formats().parseAmount(args[2]);
            if (parsed == null) {
                MessageUtil.send(player, "errors.invalid-amount", Map.of());
                return true;
            }
            amount = parsed;

            if (plugin.config().errorOnExcessArgs() && args.length > 3) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("eco", sub, label)));
                return true;
            }
        } else {
            if (plugin.config().errorOnExcessArgs() && args.length > 2) {
                MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.config().getUsage("eco", sub, label)));
                return true;
            }
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
        if (args.length < 2) {
            console.sendMessage("Usage: /" + label + " give|set|take|reset <player|@o|@a> [amount]");
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
            console.sendMessage("Usage: /" + label + " give|set|take|reset <player|@o|@a> [amount]");
            return;
        }

        String targetArg = args[1];
        Scope scope;
        OfflinePlayer target = null;

        if (targetArg.equalsIgnoreCase("@o")) scope = Scope.ONLINE;
        else if (targetArg.equalsIgnoreCase("@a")) scope = Scope.ALL;
        else {
            scope = Scope.SINGLE;
            target = resolve(null, targetArg);
            if (target == null) {
                console.sendMessage("Player not found in database.");
                return;
            }
        }

        double amount = 0;
        if (op != Op.RESET) {
            if (args.length < 3) {
                console.sendMessage("Usage: /" + label + " " + sub + " <player|@o|@a> <amount>");
                return;
            }
            Double parsed = manager.formats().parseAmount(args[2]);
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

    private String consoleName(Player sender) {
        return (sender != null) ? sender.getName() : plugin.lang().get("general.console-name");
    }

    private void handleGive(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (scope == Scope.SINGLE) {
            EconomyManager.Result res = manager.transactions().deposit(target.getUniqueId(), amount);
            String targetName = target.getName() != null ? target.getName() : targetArg;
            if (res.type() == EconomyManager.ResultType.MAX_LIMIT) {
                sendToSender(sender, "economy.give.error-exceed",
                        "Gave would exceed max limit for " + targetName,
                        Map.of("player", consoleName(sender), "amount", manager.formats().formatAmount(plugin.config().economyMaxBalance())));
                return;
            }

            if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                handleResult(sender, target, res, "economy.give.by", Map.of("amount", manager.formats().formatAmount(amount)));
            }

            if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.give.player", Map.of("player", targetName, "amount", manager.formats().formatAmount(amount)));
            } else if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.give.self", Map.of("amount", manager.formats().formatAmount(amount)));
            } else if (sender == null) {
                sendToSender(null, "economy.give.player",
                        "Gave " + manager.formats().formatAmount(amount) + " to " + targetName,
                        Map.of("player", targetName, "amount", manager.formats().formatAmount(amount)));
            }
            return;
        }

        if (scope == Scope.ONLINE) {
            if (amount <= 0) {
                sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
                return;
            }
            double max = plugin.config().economyMaxBalance();
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.error-max", "Amount exceeds max.", Map.of("amount", manager.formats().formatAmount(max)));
                return;
            }

            plugin.scheduler().runAsync(() -> {
                boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
                String actor = consoleName(sender);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    double current = manager.balances().getBalance(p.getUniqueId());
                    double allowed = (max >= 0) ? Math.max(0, max - current) : amount;
                    double given = Math.min(amount, allowed);
                    if (given <= 0) continue;

                    manager.transactions().deposit(p.getUniqueId(), given);

                    if (shouldNotify && (sender == null || !p.getUniqueId().equals(sender.getUniqueId()))) {
                        notifyPlayer(p.getUniqueId(), "economy.give.by", Map.of("player", actor, "amount", manager.formats().formatAmount(given)));
                    }
                }
                sendToSender(sender, "economy.give.online", "Gave to all online players.", Map.of("amount", manager.formats().formatAmount(amount)));
            });
            return;
        }

        if (scope == Scope.ALL) {
            if (amount <= 0) {
                sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
                return;
            }
            double max = plugin.config().economyMaxBalance();
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.error-max", "Amount exceeds max.", Map.of("amount", manager.formats().formatAmount(max)));
                return;
            }

            plugin.scheduler().runAsync(() -> {
                boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
                String actor = consoleName(sender);
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (!p.hasPlayedBefore()) continue;
                    double current = manager.balances().getBalance(p.getUniqueId());
                    double allowed = (max >= 0) ? Math.max(0, max - current) : amount;
                    double given = Math.min(amount, allowed);
                    if (given <= 0) continue;

                    manager.transactions().deposit(p.getUniqueId(), given);

                    if (shouldNotify && p.isOnline() && (sender == null || !p.getUniqueId().equals(sender.getUniqueId()))) {
                        notifyPlayer(p.getUniqueId(), "economy.give.by", Map.of("player", actor, "amount", manager.formats().formatAmount(given)));
                    }
                }
                sendToSender(sender, "economy.give.all", "Gave to all players.", Map.of("amount", manager.formats().formatAmount(amount)));
            });
        }
    }

    private void handleTake(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (amount <= 0) {
            sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
            return;
        }

        double min = plugin.config().economyMinBalance();
        double max = plugin.config().economyMaxBalance();

        if (scope == Scope.SINGLE) {
            double current = manager.balances().getBalance(target.getUniqueId());
            double allowed = (min != -1) ? current - min : amount;
            String targetName = target.getName() != null ? target.getName() : targetArg;

            if (allowed < amount) {
                sendToSender(sender, "economy.take.error-insufficient", "Insufficient funds.", Map.of("player", targetName));
                return;
            }

            EconomyManager.Result res = manager.transactions().withdraw(target.getUniqueId(), amount);
            if (res.type() == EconomyManager.ResultType.SUCCESS) {
                double actualTaken = manager.formats().round(current - res.balance());
                if (target.isOnline() && (sender != null || plugin.config().consoleToPlayerFeedback())) {
                    String actor = consoleName(sender);
                    if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                        notifyPlayer(target.getUniqueId(), "economy.take.by", Map.of("player", actor, "amount", manager.formats().formatAmount(actualTaken)));
                    }
                }

                if (sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtil.send(sender, "economy.take.player", Map.of("player", targetName, "amount", manager.formats().formatAmount(actualTaken)));
                } else if (sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                    MessageUtil.send(sender, "economy.take.self", Map.of("amount", manager.formats().formatAmount(actualTaken)));
                } else if (sender == null) {
                    sendToSender(null, "economy.take.player",
                            "Took " + manager.formats().formatAmount(actualTaken) + " from " + targetName,
                            Map.of("player", targetName, "amount", manager.formats().formatAmount(actualTaken)));
                }
            } else {
                handleResult(sender, target, res, "economy.take.by", Map.of("amount", manager.formats().formatAmount(amount)));
            }
            return;
        }

        if (scope == Scope.ONLINE) {
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.take.error-max-amount", "Amount exceeds max.", Map.of("amount", manager.formats().formatAmount(max)));
                return;
            }

            plugin.scheduler().runAsync(() -> {
                boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
                String actor = consoleName(sender);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    double current = manager.balances().getBalance(p.getUniqueId());
                    double allowed = (min != -1) ? current - min : amount;
                    double taken = Math.min(amount, allowed);
                    if (taken <= 0) continue;

                    EconomyManager.Result res = manager.transactions().withdraw(p.getUniqueId(), taken);
                    if (res.type() == EconomyManager.ResultType.SUCCESS && shouldNotify && (sender == null || !Objects.equals(p, sender))) {
                        double actualTaken = manager.formats().round(current - res.balance());
                        notifyPlayer(p.getUniqueId(), "economy.take.by", Map.of("player", actor, "amount", manager.formats().formatAmount(actualTaken)));
                    }
                }
                sendToSender(sender, "economy.take.online", "Took from all online players.", Map.of("amount", manager.formats().formatAmount(amount)));
            });
            return;
        }

        if (scope == Scope.ALL) {
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.take.error-max-amount", "Amount exceeds max.", Map.of("amount", manager.formats().formatAmount(max)));
                return;
            }

            plugin.scheduler().runAsync(() -> {
                boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
                String actor = consoleName(sender);
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (!p.hasPlayedBefore()) continue;
                    double current = manager.balances().getBalance(p.getUniqueId());
                    double allowed = (min != -1) ? current - min : amount;
                    double taken = Math.min(amount, allowed);
                    if (taken <= 0) continue;

                    EconomyManager.Result res = manager.transactions().withdraw(p.getUniqueId(), taken);
                    if (res.type() == EconomyManager.ResultType.SUCCESS && p.isOnline() && shouldNotify && (sender == null || !p.getUniqueId().equals(sender.getUniqueId()))) {
                        double actualTaken = manager.formats().round(current - res.balance());
                        notifyPlayer(p.getUniqueId(), "economy.take.by", Map.of("player", actor, "amount", manager.formats().formatAmount(actualTaken)));
                    }
                }
                sendToSender(sender, "economy.take.all", "Took from all players.", Map.of("amount", manager.formats().formatAmount(amount)));
            });
        }
    }

    private void handleSet(Player sender, Scope scope, OfflinePlayer target, String targetArg, double amount) {
        if (scope == Scope.SINGLE) {
            double min = plugin.config().economyMinBalance();
            double max = plugin.config().economyMaxBalance();

            if (min != -1 && amount < min) {
                sendToSender(sender, "economy.error-min", "Hit min limit.", Map.of("amount", manager.formats().formatAmount(min)));
                return;
            }
            if (max >= 0 && amount > max) {
                sendToSender(sender, "economy.error-max", "Hit max limit.", Map.of("amount", manager.formats().formatAmount(max)));
                return;
            }

            EconomyManager.Result res = manager.transactions().setBalance(target.getUniqueId(), amount);
            if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                handleResult(sender, target, res, "economy.set.by", Map.of("amount", manager.formats().formatAmount(amount)));
            }

            if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                String targetName = target.getName() != null ? target.getName() : targetArg;
                MessageUtil.send(sender, "economy.set.player", Map.of("player", targetName, "amount", manager.formats().formatAmount(amount)));
            } else if (res.type() == EconomyManager.ResultType.SUCCESS && sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.set.self", Map.of("amount", manager.formats().formatAmount(amount)));
            } else if (res.type() == EconomyManager.ResultType.SUCCESS && sender == null) {
                String targetName = target.getName() != null ? target.getName() : targetArg;
                sendToSender(null, "economy.set.player", "Set " + targetName + "'s balance to " + manager.formats().formatAmount(amount), Map.of("player", targetName, "amount", manager.formats().formatAmount(amount)));
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            AtomicBoolean anyInvalid = new AtomicBoolean(false);
            AtomicBoolean anyMin = new AtomicBoolean(false);
            AtomicBoolean anyMax = new AtomicBoolean(false);
            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);

            if (scope == Scope.ONLINE) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    EconomyManager.Result res = manager.transactions().setBalance(p.getUniqueId(), amount);
                    if (res.type() == EconomyManager.ResultType.SUCCESS && shouldNotify && !Objects.equals(p, sender)) {
                        notifyPlayer(p.getUniqueId(), "economy.set.by", Map.of("player", actor, "amount", manager.formats().formatAmount(amount)));
                    }
                    if (res.type() == EconomyManager.ResultType.INVALID) anyInvalid.set(true);
                    else if (res.type() == EconomyManager.ResultType.MIN_LIMIT) anyMin.set(true);
                    else if (res.type() == EconomyManager.ResultType.MAX_LIMIT) anyMax.set(true);
                }
            } else {
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (!p.hasPlayedBefore()) continue;
                    EconomyManager.Result res = manager.transactions().setBalance(p.getUniqueId(), amount);
                    if (res.type() == EconomyManager.ResultType.SUCCESS && p.isOnline() && shouldNotify && (sender == null || !p.getUniqueId().equals(sender.getUniqueId()))) {
                        notifyPlayer(p.getUniqueId(), "economy.set.by", Map.of("player", actor, "amount", manager.formats().formatAmount(amount)));
                    }
                    if (res.type() == EconomyManager.ResultType.INVALID) anyInvalid.set(true);
                    else if (res.type() == EconomyManager.ResultType.MIN_LIMIT) anyMin.set(true);
                    else if (res.type() == EconomyManager.ResultType.MAX_LIMIT) anyMax.set(true);
                }
            }

            if (anyInvalid.get()) sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
            else if (anyMin.get()) sendToSender(sender, "economy.error-min", "Hit min limit.", Map.of("amount", manager.formats().formatAmount(plugin.config().economyMinBalance())));
            else if (anyMax.get()) sendToSender(sender, "economy.error-max", "Hit max limit.", Map.of("amount", manager.formats().formatAmount(plugin.config().economyMaxBalance())));
            else sendToSender(sender, scope == Scope.ONLINE ? "economy.set.online" : "economy.set.all", "Set balances.", Map.of("amount", manager.formats().formatAmount(amount)));
        });
    }

    private void handleReset(Player sender, Scope scope, OfflinePlayer target, String targetArg) {
        if (scope == Scope.SINGLE) {
            manager.transactions().resetBalance(target.getUniqueId());
            if (target.isOnline() && (sender != null || plugin.config().consoleToPlayerFeedback())) {
                String actor = consoleName(sender);
                if (!(sender != null && sender.getUniqueId().equals(target.getUniqueId()))) {
                    notifyPlayer(target.getUniqueId(), "economy.reset.by", Map.of("player", actor));
                }
            }
            if (sender != null && !sender.getUniqueId().equals(target.getUniqueId())) {
                String targetName = target.getName() != null ? target.getName() : targetArg;
                MessageUtil.send(sender, "economy.reset.player", Map.of("player", targetName));
            } else if (sender != null && sender.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(sender, "economy.reset.self", Map.of());
            } else if (sender == null) {
                String targetName = target.getName() != null ? target.getName() : targetArg;
                Bukkit.getConsoleSender().sendMessage("Reset " + targetName + "'s balance.");
            }
            return;
        }

        plugin.scheduler().runAsync(() -> {
            boolean shouldNotify = (sender != null) || plugin.config().consoleToPlayerFeedback();
            String actor = consoleName(sender);
            if (scope == Scope.ONLINE) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    manager.transactions().resetBalance(p.getUniqueId());
                    if (shouldNotify && (sender == null || !Objects.equals(p, sender))) {
                        notifyPlayer(p.getUniqueId(), "economy.reset.by", Map.of("player", actor));
                    }
                }
                sendToSender(sender, "economy.reset.online", "Reset online players.", Map.of());
            } else {
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (!p.hasPlayedBefore()) continue;
                    manager.transactions().resetBalance(p.getUniqueId());
                    if (p.isOnline() && shouldNotify && (sender == null || !p.getUniqueId().equals(sender.getUniqueId()))) {
                        notifyPlayer(p.getUniqueId(), "economy.reset.by", Map.of("player", actor));
                    }
                }
                sendToSender(sender, "economy.reset.all", "Reset all players.", Map.of());
            }
        });
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
            case MAX_LIMIT -> sendToSender(sender, "economy.error-max", "Hit max limit.", Map.of("amount", manager.formats().formatAmount(plugin.config().economyMaxBalance())));
            case MIN_LIMIT -> sendToSender(sender, "economy.error-min", "Hit min limit.", Map.of("amount", manager.formats().formatAmount(plugin.config().economyMinBalance())));
            case INVALID -> sendToSender(sender, "errors.invalid-amount", "Invalid amount.", Map.of());
            default -> {}
        }
    }

    private OfflinePlayer resolve(Player sender, String name) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) return online;
        }
        UUID cached = plugin.getNameCache().get(name.toLowerCase(Locale.ROOT));
        if (cached != null) return Bukkit.getOfflinePlayer(cached);
        if (sender != null) MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("aircore.command.eco") && !sender.hasPermission("aircore.command.eco.*")) return completions;

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("give", "take", "set", "reset")) {
                if (sender.hasPermission("aircore.command.eco." + sub) || sender.hasPermission("aircore.command.eco.*")) {
                    if (sub.startsWith(input)) completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String input = args[1].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("aircore.command.eco." + sub + ".online") || sender.hasPermission("aircore.command.eco.*")) if ("@o".startsWith(input)) completions.add("@o");
            if (sender.hasPermission("aircore.command.eco." + sub + ".all") || sender.hasPermission("aircore.command.eco.*")) if ("@a".startsWith(input)) completions.add("@a");
            Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).limit(20).forEach(completions::add);
        }
        return completions;
    }
}