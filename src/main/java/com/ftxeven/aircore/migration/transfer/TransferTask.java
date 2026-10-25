package com.ftxeven.aircore.migration.transfer;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.config.StorageConfig;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.migration.MigrationOptions;
import com.ftxeven.aircore.migration.MigrationReport;
import com.ftxeven.aircore.model.PlayerProfile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TransferTask {

    private static final int PROGRESS_INTERVAL = 250;
    private static final int PLAYER_CHUNK = 500;

    private final AirCore plugin;
    private final StorageConfig.Database from;
    private final StorageConfig.Database to;
    private final MigrationOptions options;
    private final Consumer<String> feedback;
    private final Logger logger;
    private final MigrationReport report = new MigrationReport();

    public TransferTask(AirCore plugin, StorageConfig.Database from, StorageConfig.Database to,
                        MigrationOptions options, Consumer<String> feedback) {
        this.plugin = plugin;
        this.from = from;
        this.to = to;
        this.options = options;
        this.feedback = feedback;
        this.logger = plugin.getLogger();
    }

    public void run() {
        long start = System.currentTimeMillis();
        feedback.accept("Transferring " + label(from) + " -> " + label(to)
                + (options.dryRun() ? " (dry run, nothing will be written)" : ""));

        try {
            // whatever is still queued in memory must reach the live database before anything is read
            plugin.cache().flush();

            try (DatabaseManager source = open(from); DatabaseManager target = open(to)) {
                copy(source, target);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Transfer aborted", e);
            feedback.accept("Transfer aborted: " + e.getMessage());
            return;
        }

        summarize(System.currentTimeMillis() - start);
    }

    private DatabaseManager open(StorageConfig.Database settings) {
        DatabaseManager database = new DatabaseManager(plugin, settings);
        if (!database.connect()) {
            throw new IllegalStateException("Could not connect to " + label(settings) + " storage, check the console for details");
        }
        return database;
    }

    private void copy(DatabaseManager source, DatabaseManager target) {
        StorageCopier copier = new StorageCopier(source, target, options, report);

        List<UUID> uuids = List.copyOf(source.players().findAllUuids());
        feedback.accept("Found " + uuids.size() + " player record(s).");

        int processed = 0;
        for (int offset = 0; offset < uuids.size(); offset += PLAYER_CHUNK) {
            List<UUID> chunk = uuids.subList(offset, Math.min(uuids.size(), offset + PLAYER_CHUNK));
            for (PlayerProfile profile : source.players().findAll(chunk).values()) {
                copyPlayer(copier, profile);
                if (++processed % PROGRESS_INTERVAL == 0) {
                    feedback.accept("  " + processed + "/" + uuids.size() + " players processed");
                }
            }
        }

        copier.copyServerData();
        copier.finish();
    }

    private void copyPlayer(StorageCopier copier, PlayerProfile profile) {
        try {
            copier.copyPlayer(profile);
        } catch (RuntimeException e) {
            report.failed(MigrationReport.Category.PLAYERS, profile.uuid() + ": " + e.getMessage());
            logger.log(Level.WARNING, "Transfer failed for " + profile.uuid(), e);
        }
    }

    private void summarize(long elapsedMillis) {
        feedback.accept("Transfer finished in " + (elapsedMillis / 1000.0) + "s"
                + (options.dryRun() ? " (dry run)" : ""));
        report.summarize(feedback, logger);

        if (options.dryRun()) {
            return;
        }
        feedback.accept(report.hasFailures()
                ? "Some rows failed to transfer. Fix the cause (see the warnings) and run the transfer again before switching."
                : "Set 'database.driver' to " + to.driver() + " in storage.yml if it isn't already, "
                  + "then restart the server before letting players back in.");
    }

    private static String label(StorageConfig.Database settings) {
        return settings.driver().name().toLowerCase(Locale.ROOT);
    }
}