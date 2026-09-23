package com.ftxeven.aircore.migration;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.migration.model.LegacyPlayer;
import com.ftxeven.aircore.migration.model.LegacyServerData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MigrationTask {

    private static final int PROGRESS_INTERVAL = 250;
    private static final int WARNINGS_SHOWN = 10;

    private final MigrationSource source;
    private final Path root;
    private final MigrationOptions options;
    private final Consumer<String> feedback;
    private final Logger logger;
    private final MigrationReport report = new MigrationReport();
    private final MigrationWriter writer;

    public MigrationTask(AirCore plugin, MigrationSource source, Path root, MigrationOptions options, Consumer<String> feedback) {
        this.source = source;
        this.root = root;
        this.options = options;
        this.feedback = feedback;
        this.logger = plugin.getLogger();
        this.writer = new MigrationWriter(plugin.database(), plugin.cache(), plugin.configs(), options, report);
    }

    public void run() {
        long start = System.currentTimeMillis();
        feedback.accept("Reading " + source.displayName() + " data from " + root
                + (options.dryRun() ? " (dry run, nothing will be written)" : ""));

        try (MigrationSource.Reader reader = source.open(root)) {
            List<MigrationSource.Candidate> candidates = reader.players();
            feedback.accept("Found " + candidates.size() + " player record(s).");

            int processed = 0;
            for (MigrationSource.Candidate candidate : candidates) {
                try {
                    Optional<LegacyPlayer> legacy = reader.load(candidate);
                    if (legacy.isEmpty()) {
                        report.skipped(MigrationReport.Category.PLAYERS);
                    } else {
                        writer.writePlayer(legacy.get());
                    }
                } catch (Exception e) {
                    report.failed(MigrationReport.Category.PLAYERS, candidate.origin() + ": " + e.getMessage());
                    logger.log(Level.WARNING, "Migration failed for " + candidate.origin(), e);
                }

                if (++processed % PROGRESS_INTERVAL == 0) {
                    feedback.accept("  " + processed + "/" + candidates.size() + " players processed");
                }
            }

            LegacyServerData serverData = reader.serverData();
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
        report.lines().forEach(feedback);

        List<String> warnings = report.warnings();
        if (!warnings.isEmpty()) {
            feedback.accept(warnings.size() + " warning(s):");
            warnings.stream().limit(WARNINGS_SHOWN).forEach(warning -> feedback.accept("  - " + warning));
            if (warnings.size() > WARNINGS_SHOWN || report.droppedWarnings() > 0) {
                feedback.accept("  ... the full list is in the server log.");
            }
            warnings.forEach(warning -> logger.warning("[migration] " + warning));
        }

        if (!source.notMigrated().isEmpty()) {
            feedback.accept("Not carried over: " + String.join(", ", source.notMigrated()));
        }
        if (!options.dryRun()) {
            feedback.accept("Restart the server before letting players back in.");
        }
    }
}