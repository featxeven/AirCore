package com.ftxeven.aircore.command;

import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandRegistry;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.service.CommandCooldownService;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CommandDispatcher {

    private final Messenger messenger;
    private final ConfigManager configs;
    private final ServiceManager services;

    public CommandDispatcher(Messenger messenger, ConfigManager configs, ServiceManager services) {
        this.messenger = messenger;
        this.configs = configs;
        this.services = services;
    }

    public void dispatch(CommandRegistry registry, CommandSender sender, String label, String[] args, Runnable onNoMatch) {
        dispatch(registry, sender, label, args, false, onNoMatch);
    }

    public void dispatch(CommandRegistry registry, CommandSender sender, String label, String[] args, boolean viaShortcut, Runnable onNoMatch) {
        if (args.length == 0) {
            onNoMatch.run();
            return;
        }

        Optional<CommandHandler> matched = registry.match(args[0]);
        if (matched.isEmpty()) {
            onNoMatch.run();
            return;
        }

        run(matched.get(), sender, label, viaShortcut ? null : args[0], tail(args));
    }

    public void dispatchDirect(CommandHandler commandHandler, CommandSender sender, String label, String[] args) {
        run(commandHandler, sender, label, null, args);
    }

    public Executor asExecutor(CommandHandler commandHandler) {
        return new Executor(this, commandHandler);
    }

    private void run(CommandHandler commandHandler, CommandSender sender, String label, String subLabel, String[] args) {
        if (!commandHandler.enabled()) {
            return;
        }

        if (!commandHandler.hasPermission(sender)) {
            messenger.send(sender, configs.lang().get("errors.access.no-permission"), Map.of("permission", commandHandler.permission()));
            return;
        }

        if (commandHandler.playerOnly() && !(sender instanceof Player)) {
            messenger.send(sender, configs.lang().get("errors.access.player-only"));
            return;
        }

        if (args.length < commandHandler.minArgs(sender, args)) {
            sendUsageError(sender, "errors.access.incorrect-usage", commandHandler, label, subLabel);
            return;
        }

        int maxArgs = commandHandler.maxArgs(sender, args);
        if (maxArgs >= 0 && args.length > maxArgs && configs.main().general().strictArgs()) {
            sendUsageError(sender, "errors.access.too-many-arguments", commandHandler, label, subLabel);
            return;
        }

        if (!checkCommandCooldown(commandHandler, sender, args)) {
            return;
        }

        commandHandler.execute(sender, label, subLabel, args);
    }

    private void sendUsageError(CommandSender sender, String langKey, CommandHandler commandHandler, String label, String subLabel) {
        String usage = CommandDispatch.format(commandHandler.usage(sender), label, subLabel);
        messenger.send(sender, configs.lang().get(langKey), Map.of("usage", usage));
    }

    // Command cooldowns

    private boolean checkCommandCooldown(CommandHandler commandHandler, CommandSender sender, String[] args) {
        CommandCooldownService.Verdict verdict = services.commandCooldowns().check(commandHandler.name(), sender, args);
        if (!(verdict instanceof CommandCooldownService.Verdict.Blocked blocked)) {
            return true;
        }

        String timeout = TimeFormatter.duration(blocked.expiresAt(), configs.main().formatting(), configs.lang());
        List<String> template = blocked.customMessage() != null
                ? List.of(blocked.customMessage())
                : configs.lang().get("errors.general.command-cooldown");
        messenger.send(sender, template, Map.of("timeout", timeout));
        return false;
    }

    // Tab-complete

    public List<String> tabComplete(CommandRegistry registry, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return matchingNames(registry, sender, args[0]);
        }

        return registry.match(args[0])
                .map(commandHandler -> tabCompleteDirect(commandHandler, sender, tail(args)))
                .orElse(List.of());
    }

    public List<String> tabCompleteDirect(CommandHandler commandHandler, CommandSender sender, String[] args) {
        return commandHandler.isVisibleTo(sender) ? commandHandler.tabComplete(sender, args) : List.of();
    }

    private List<String> matchingNames(CommandRegistry registry, CommandSender sender, String prefix) {
        List<String> candidates = new ArrayList<>();
        for (CommandHandler commandHandler : registry.all()) {
            if (!commandHandler.isVisibleTo(sender)) {
                continue;
            }
            candidates.add(commandHandler.name());
            candidates.addAll(commandHandler.aliases());
        }
        return filterPrefix(candidates, prefix);
    }

    public static List<String> filterPrefix(List<String> candidates, String typed) {
        String lowerTyped = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerTyped)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static String[] tail(String[] args) {
        return args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    }

    public static final class Executor implements CommandExecutor, TabCompleter {

        private final CommandDispatcher dispatcher;
        private final CommandHandler commandHandler;

        private Executor(CommandDispatcher dispatcher, CommandHandler commandHandler) {
            this.dispatcher = dispatcher;
            this.commandHandler = commandHandler;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            dispatcher.dispatchDirect(commandHandler, sender, label, args);
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            return dispatcher.tabCompleteDirect(commandHandler, sender, args);
        }
    }
}