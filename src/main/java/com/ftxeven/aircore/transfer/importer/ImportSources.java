package com.ftxeven.aircore.transfer.importer;

import com.ftxeven.aircore.transfer.importer.source.essentials.EssentialsSource;
import com.ftxeven.aircore.transfer.importer.source.legacy.LegacySource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ImportSources {

    private static final List<ImportSource> SOURCES = List.of(
            new EssentialsSource(),
            new LegacySource()
    );

    private ImportSources() {
    }

    public static List<ImportSource> all() {
        return SOURCES;
    }

    public static Optional<ImportSource> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String needle = id.toLowerCase(Locale.ROOT);
        return SOURCES.stream().filter(source -> source.id().equals(needle)).findFirst();
    }

    public static List<String> ids() {
        return SOURCES.stream().map(ImportSource::id).toList();
    }
}