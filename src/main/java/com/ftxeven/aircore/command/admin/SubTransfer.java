package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.config.StorageConfig;
import com.ftxeven.aircore.migration.MigrationOptions;
import com.ftxeven.aircore.migration.transfer.TransferTask;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SubTransfer extends DataTaskCommand {

    private static final List<String> STORAGES = Arrays.stream(StorageConfig.Driver.values())
            .map(driver -> driver.name().toLowerCase(Locale.ROOT))
            .toList();

    public SubTransfer(AirCore plugin) {
        super(plugin, "transfer");
    }

    @Override
    public String name() { return "transfer"; }

    @Override
    public int minArgs() { return 2; }

    @Override
    public String usage() { return "/aircore transfer <from> <to> [flags]"; }

    @Override
    protected String onlineConsequence() { return "Their data can still change while it is copied."; }

    @Override
    protected void handle(CommandSender sender, String[] args) {
        Optional<StorageConfig.Driver> from = storage(sender, args[0]);
        if (from.isEmpty()) {
            return;
        }
        Optional<StorageConfig.Driver> to = storage(sender, args[1]);
        if (to.isEmpty()) {
            return;
        }
        if (from.get().canonical() == to.get().canonical()) {
            reply(sender, "Source and target are the same storage.");
            return;
        }

        Optional<MigrationOptions> parsed = parseOptions(sender, Arrays.asList(args).subList(2, args.length));
        if (parsed.isEmpty()) {
            return;
        }
        MigrationOptions options = parsed.get();

        // both ends use the settings of storage.yml, each with its own driver section
        StorageConfig.Database active = plugin.configs().storage().database();
        StorageConfig.Database source = active.withDriver(from.get());
        StorageConfig.Database target = active.withDriver(to.get());

        launch(sender, feedback -> new TransferTask(plugin, source, target, options, feedback).run());
    }

    private Optional<StorageConfig.Driver> storage(CommandSender sender, String typed) {
        for (StorageConfig.Driver driver : StorageConfig.Driver.values()) {
            if (driver.name().equalsIgnoreCase(typed)) {
                return Optional.of(driver);
            }
        }
        reply(sender, "Unknown storage '" + typed + "'. Available: " + String.join(", ", STORAGES));
        return Optional.empty();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 2) {
            return CommandDispatcher.filterPrefix(STORAGES, args[args.length - 1]);
        }
        return completeFlags(args, 2);
    }
}