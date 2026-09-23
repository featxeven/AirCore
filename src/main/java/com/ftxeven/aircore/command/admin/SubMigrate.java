package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.migration.MigrationOptions;
import com.ftxeven.aircore.migration.MigrationSource;
import com.ftxeven.aircore.migration.MigrationSources;
import com.ftxeven.aircore.migration.MigrationTask;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class SubMigrate implements CommandHandler {

    private static final List<String> FLAGS = List.of("--dry-run", "--overwrite", "--force");

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;
    private final AtomicBoolean running = new AtomicBoolean();

    public SubMigrate(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "migrate"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/aircore migrate <" + String.join("|", MigrationSources.ids()) + "> [path] [flags]"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (sender instanceof Player) {
            messenger.send(sender, configs.lang().get("errors.access.console-only"));
            return;
        }

        Optional<MigrationSource> maybeSource = MigrationSources.byId(args[0]);
        if (maybeSource.isEmpty()) {
            reply(sender, "Unknown migration source '" + args[0] + "'. Available: " + String.join(", ", MigrationSources.ids()));
            return;
        }
        MigrationSource source = maybeSource.get();

        String pathArg = null;
        boolean dryRun = false;
        boolean overwrite = false;
        boolean force = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                switch (arg.toLowerCase(Locale.ROOT)) {
                    case "--dry-run" -> dryRun = true;
                    case "--overwrite" -> overwrite = true;
                    case "--force" -> force = true;
                    default -> {
                        reply(sender, "Unknown flag '" + arg + "'. Usage: " + usage());
                        return;
                    }
                }
            } else {
                pathArg = pathArg == null ? arg : pathArg + " " + arg; // paths with spaces
            }
        }

        MigrationOptions options = new MigrationOptions(dryRun, overwrite, force);

        if (!options.force() && !Bukkit.getOnlinePlayers().isEmpty()) {
            reply(sender, Bukkit.getOnlinePlayers().size() + " player(s) are online. Their data would be overwritten "
                    + "when they disconnect. Kick everyone first, or add --force.");
            return;
        }

        Path root;
        try {
            root = resolvePath(source, pathArg);
        } catch (InvalidPathException e) {
            reply(sender, "Invalid path: " + e.getMessage());
            return;
        }

        if (!Files.exists(root)) {
            reply(sender, "Nothing found at " + root);
            reply(sender, source.pathHint());
            return;
        }

        if (!running.compareAndSet(false, true)) {
            reply(sender, "A migration is already running.");
            return;
        }

        if (!options.dryRun()) {
            reply(sender, "Back up your database before continuing if you haven't already.");
        }

        Path target = root;
        Scheduler.runAsync(() -> {
            try {
                new MigrationTask(plugin, source, target, options, line -> Scheduler.runGlobal(() -> reply(sender, line))).run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Migration failed", e);
                Scheduler.runGlobal(() -> reply(sender, "Migration failed: " + e.getMessage()));
            } finally {
                running.set(false);
            }
        });
    }

    // plugins/AirCore -> plugins -> server root
    private Path resolvePath(MigrationSource source, String pathArg) {
        Path serverRoot = plugin.getDataFolder().getAbsoluteFile().toPath().getParent().getParent();
        Path path = Path.of(pathArg == null ? source.defaultPath() : pathArg);
        return (path.isAbsolute() ? path : serverRoot.resolve(path)).normalize();
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(MigrationSources.ids(), args[0]);
        }
        List<String> remaining = new ArrayList<>(FLAGS);
        for (int i = 1; i < args.length - 1; i++) {
            remaining.remove(args[i].toLowerCase(Locale.ROOT));
        }
        return CommandDispatcher.filterPrefix(remaining, args[args.length - 1]);
    }
}