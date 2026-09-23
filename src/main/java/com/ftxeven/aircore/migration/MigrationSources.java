package com.ftxeven.aircore.migration;

import com.ftxeven.aircore.migration.source.essentials.EssentialsSource;
import com.ftxeven.aircore.migration.source.legacy.LegacyAirCoreSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MigrationSources {

    private static final List<MigrationSource> SOURCES = List.of(
            new EssentialsSource(),
            new LegacyAirCoreSource()
    );

    private MigrationSources() {
    }

    public static List<MigrationSource> all() {
        return SOURCES;
    }

    public static Optional<MigrationSource> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String needle = id.toLowerCase(Locale.ROOT);
        return SOURCES.stream().filter(source -> source.id().equals(needle)).findFirst();
    }

    public static List<String> ids() {
        return SOURCES.stream().map(MigrationSource::id).toList();
    }
}