package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.transfer.TransferOptions;
import com.ftxeven.aircore.transfer.importer.ImportSource;
import com.ftxeven.aircore.transfer.importer.ImportSources;
import com.ftxeven.aircore.transfer.importer.ImportTask;
import org.bukkit.command.CommandSender;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SubMigrate extends DataTaskCommand {

    public SubMigrate(AirCore plugin) {
        super(plugin, "migration");
    }

    @Override
    public String name() { return "migrate"; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public String usage() { return "/aircore migrate <" + String.join("|", ImportSources.ids()) + "> [path] [flags]"; }

    @Override
    protected String onlineConsequence() { return "Their data would be overwritten when they disconnect."; }

    @Override
    protected void handle(CommandSender sender, String[] args) {
        Optional<ImportSource> maybeSource = ImportSources.byId(args[0]);
        if (maybeSource.isEmpty()) {
            reply(sender, "Unknown migration source '" + args[0] + "'. Available: " + String.join(", ", ImportSources.ids()));
            return;
        }
        ImportSource source = maybeSource.get();

        String pathArg = null;
        List<String> flags = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                flags.add(args[i]);
            } else {
                pathArg = pathArg == null ? args[i] : pathArg + " " + args[i]; // paths with spaces
            }
        }

        Optional<TransferOptions> parsed = parseOptions(sender, flags);
        if (parsed.isEmpty()) {
            return;
        }
        TransferOptions options = parsed.get();

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

        launch(sender, feedback -> {
            if (!options.dryRun()) {
                feedback.accept("Back up your database before continuing if you haven't already.");
            }
            new ImportTask(plugin, source, root, options, feedback).run();
        });
    }

    // plugins/AirCore -> plugins -> server root
    private Path resolvePath(ImportSource source, String pathArg) {
        Path serverRoot = plugin.getDataFolder().getAbsoluteFile().toPath().getParent().getParent();
        Path path = Path.of(pathArg == null ? source.defaultPath() : pathArg);
        return (path.isAbsolute() ? path : serverRoot.resolve(path)).normalize();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(ImportSources.ids(), args[0]);
        }
        return completeFlags(args, 1);
    }
}