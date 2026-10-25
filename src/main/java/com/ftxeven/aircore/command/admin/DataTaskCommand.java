package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.migration.MigrationOptions;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

// Shared shape of the console-only admin commands that rewrite stored data (migrate, transfer)
abstract class DataTaskCommand implements CommandHandler {

    @FunctionalInterface
    protected interface Task {
        void run(Consumer<String> feedback) throws Exception;
    }

    private static final List<String> FLAGS = List.of("--dry-run", "--overwrite", "--force");

    protected final AirCore plugin;
    private final String noun;
    private final String title;
    private final AtomicBoolean running = new AtomicBoolean();

    protected DataTaskCommand(AirCore plugin, String noun) {
        this.plugin = plugin;
        this.noun = noun;
        this.title = noun.substring(0, 1).toUpperCase(Locale.ROOT) + noun.substring(1);
    }

    @Override
    public final String permission() { return Permissions.ADMIN; }

    @Override
    public final int maxArgs() { return -1; }

    @Override
    public final void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (sender instanceof Player) {
            plugin.messenger().send(sender, plugin.configs().lang().get("errors.access.console-only"));
            return;
        }
        handle(sender, args);
    }

    // the command's own arguments, once the console-only guard has passed
    protected abstract void handle(CommandSender sender, String[] args);

    // what happens to players who are online while the data is rewritten, shown when they block the run
    protected abstract String onlineConsequence();

    // Flags and guards

    protected final Optional<MigrationOptions> parseOptions(CommandSender sender, List<String> flags) {
        boolean dryRun = false;
        boolean overwrite = false;
        boolean force = false;

        for (String flag : flags) {
            switch (flag.toLowerCase(Locale.ROOT)) {
                case "--dry-run" -> dryRun = true;
                case "--overwrite" -> overwrite = true;
                case "--force" -> force = true;
                default -> {
                    reply(sender, "Unknown flag '" + flag + "'. Usage: " + usage());
                    return Optional.empty();
                }
            }
        }

        if (!force && !Bukkit.getOnlinePlayers().isEmpty()) {
            reply(sender, Bukkit.getOnlinePlayers().size() + " player(s) are online. " + onlineConsequence()
                    + " Kick everyone first, or add --force.");
            return Optional.empty();
        }
        return Optional.of(new MigrationOptions(dryRun, overwrite, force));
    }

    // Running

    protected final void launch(CommandSender sender, Task task) {
        if (!running.compareAndSet(false, true)) {
            reply(sender, "A " + noun + " is already running.");
            return;
        }

        Scheduler.runAsync(() -> {
            try {
                task.run(line -> Scheduler.runGlobal(() -> reply(sender, line)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, title + " failed", e);
                Scheduler.runGlobal(() -> reply(sender, title + " failed: " + e.getMessage()));
            } finally {
                running.set(false);
            }
        });
    }

    protected final void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    // Tab-complete

    protected final List<String> completeFlags(String[] args, int firstFlag) {
        List<String> remaining = new ArrayList<>(FLAGS);
        for (int i = firstFlag; i < args.length - 1; i++) {
            remaining.remove(args[i].toLowerCase(Locale.ROOT));
        }
        return CommandDispatcher.filterPrefix(remaining, args[args.length - 1]);
    }
}