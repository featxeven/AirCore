package com.ftxeven.aircore.database.migration;

import java.util.List;

public interface MigrationRunner {
    int currentVersion() throws Exception;

    void migrate(List<Migration> available) throws Exception;

    record Migration(int version, String description, String fileName, String content) {}
}