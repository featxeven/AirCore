package com.ftxeven.aircore.migration;

import com.ftxeven.aircore.migration.model.LegacyPlayer;
import com.ftxeven.aircore.migration.model.LegacyServerData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationSource {

    String id();

    String displayName();

    // relative to the server root, used when the admin gives no explicit path
    String defaultPath();

    String pathHint();

    // things this source knowingly leaves behind, printed in the summary
    default List<String> notMigrated() {
        return List.of();
    }

    Reader open(Path path) throws Exception;

    // identity + ordering info, kept cheap so millions of records don't have to sit in memory at once
    record Candidate(UUID uuid, String name, long order, String origin) {}

    interface Reader extends AutoCloseable {

        // oldest first, so join numbers are handed out in roughly the original order
        List<Candidate> players() throws Exception;

        Optional<LegacyPlayer> load(Candidate candidate) throws Exception;

        LegacyServerData serverData() throws Exception;

        @Override
        void close();
    }
}