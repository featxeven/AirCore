package com.ftxeven.aircore.transfer.importer;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.transfer.TransferOptions;
import com.ftxeven.aircore.transfer.TransferReport;
import com.ftxeven.aircore.transfer.importer.model.ImportedPlayer;
import com.ftxeven.aircore.transfer.importer.model.ImportedServerData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ImportTask {

    private static final int PROGRESS_INTERVAL = 250;

    private final ImportSource source;
    private final Path root;
    private final TransferOptions options;
    private final Consumer<String> feedback;
    private final Logger logger;
    private final TransferReport report = new TransferReport();
    private final ImportWriter writer;

    public ImportTask(AirCore plugin, ImportSource source, Path root, TransferOptions options, Consumer<String> feedback) {
        this.source = source;
        this.root = root;
        this.options = options;
        this.feedback = feedback;
        this.logger = plugin.getLogger();
        this.writer = new ImportWriter(plugin.database(), plugin.cache(), plugin.configs(), options, report);
    }

    public void run() {
        long start = System.currentTimeMillis();
        feedback.accept("Reading " + source.displayName() + " data from " + root
                + (options.dryRun() ? " (dry run, nothing will be written)" : ""));

        try (ImportSource.Reader reader = source.open(root)) {
            List<ImportSource.Candidate> candidates = reader.players();
            feedback.accept("Found " + candidates.size() + " player record(s).");

            int processed = 0;
            for (ImportSource.Candidate candidate : candidates) {
                try {
                    Optional<ImportedPlayer> legacy = reader.load(candidate);
                    if (legacy.isEmpty()) {
                        report.skipped(TransferReport.Category.PLAYERS);
                    } else {
                        writer.writePlayer(legacy.get());
                    }
                } catch (Exception e) {
                    report.failed(TransferReport.Category.PLAYERS, candidate.origin() + ": " + e.getMessage());
                    logger.log(Level.WARNING, "Migration failed for " + candidate.origin(), e);
                }

                if (++processed % PROGRESS_INTERVAL == 0) {
                    feedback.accept("  " + processed + "/" + candidates.size() + " players processed");
                }
            }

            ImportedServerData serverData = reader.serverData();
            writer.writeServerData(serverData);
            writer.finish();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Migration aborted", e);
            feedback.accept("Migration aborted: " + e.getMessage());
            feedback.accept(source.pathHint());
            return;
        }

        summarize(System.currentTimeMillis() - start);
    }

    private void summarize(long elapsedMillis) {
        feedback.accept("Migration finished in " + (elapsedMillis / 1000.0) + "s"
                + (options.dryRun() ? " (dry run)" : ""));
        report.summarize(feedback, logger);

        if (!source.notMigrated().isEmpty()) {
            feedback.accept("Not carried over: " + String.join(", ", source.notMigrated()));
        }
        if (!options.dryRun()) {
            feedback.accept("Restart the server before letting players back in.");
        }
    }
}