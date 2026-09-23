package com.ftxeven.aircore.migration;

public record MigrationOptions(boolean dryRun, boolean overwrite, boolean force) {
}