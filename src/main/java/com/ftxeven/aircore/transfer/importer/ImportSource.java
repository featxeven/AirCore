package com.ftxeven.aircore.transfer.importer;

import com.ftxeven.aircore.transfer.importer.model.ImportedPlayer;
import com.ftxeven.aircore.transfer.importer.model.ImportedServerData;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportSource {

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

    // identity + ordering info
    record Candidate(UUID uuid, String name, long order, String origin) {}

    interface Reader extends AutoCloseable {

        // oldest first, so join numbers are handed out in roughly the original order
        List<Candidate> players() throws Exception;

        Optional<ImportedPlayer> load(Candidate candidate) throws Exception;

        ImportedServerData serverData() throws Exception;

        @Override
        void close();
    }
}