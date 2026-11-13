package com.ftxeven.aircore.transfer;

public record TransferOptions(boolean dryRun, boolean overwrite, boolean force) {
}