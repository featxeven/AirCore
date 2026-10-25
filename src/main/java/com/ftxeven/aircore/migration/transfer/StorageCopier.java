package com.ftxeven.aircore.migration.transfer;

import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.repository.BlockRepository;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.database.repository.PersistentBossbarRepository;
import com.ftxeven.aircore.database.repository.VariableRepository;
import com.ftxeven.aircore.migration.MigrationOptions;
import com.ftxeven.aircore.migration.MigrationReport;
import com.ftxeven.aircore.migration.MigrationReport.Category;
import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.service.PlayerService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

final class StorageCopier {

    private final DatabaseManager source;
    private final DatabaseManager target;
    private final MigrationOptions options;
    private final MigrationReport report;
    private final Instant now = Instant.now();

    StorageCopier(DatabaseManager source, DatabaseManager target, MigrationOptions options, MigrationReport report) {
        this.source = source;
        this.target = target;
        this.options = options;
        this.report = report;
    }

    // Players

    void copyPlayer(PlayerProfile profile) {
        UUID owner = profile.uuid();
        copy(Category.PLAYERS,
                () -> target.players().find(owner).isPresent(),
                () -> target.players().importProfile(profile, PlayerService.normalizedNickname(profile.nickname())));

        copyHomes(owner);
        copyBlocks(owner);
        copyCooldowns(owner);
        copyVariables(owner.toString());
        copyInventory(owner);
    }

    private void copyHomes(UUID owner) {
        for (Home home : source.homes().findAll(owner)) {
            copy(Category.HOMES,
                    () -> target.homes().find(owner, home.name()).isPresent(),
                    () -> target.homes().save(home));
        }
    }

    private void copyBlocks(UUID owner) {
        for (BlockRepository.Entry block : source.blocks().export(owner)) {
            copy(Category.BLOCKS,
                    () -> target.blocks().isBlocked(owner, block.blocked()),
                    () -> target.blocks().block(owner, block.blocked(), block.blockedAt()));
        }
    }

    private void copyCooldowns(UUID owner) {
        for (Cooldown cooldown : source.cooldowns().findAllActive(owner, now)) {
            copy(Category.COOLDOWNS,
                    () -> target.cooldowns().find(owner, cooldown.scope(), cooldown.key(), cooldown.arg(), now).isPresent(),
                    () -> target.cooldowns().set(owner, cooldown.scope(), cooldown.key(), cooldown.arg(), cooldown.expiresAt()));
        }
    }

    private void copyInventory(UUID owner) {
        source.inventories().find(owner).ifPresent(inventory -> copy(Category.INVENTORIES,
                () -> target.inventories().find(owner).isPresent(),
                () -> {
                    target.inventories().saveContents(owner, inventory.contents(), inventory.heldSlot());
                    target.inventories().saveEnderChest(owner, inventory.enderChest());
                }));
    }

    // Variables are written in one batch per owner

    private void copyVariables(String owner) {
        List<VariableRepository.Change> rows = source.variables().export(owner);
        if (rows.isEmpty()) {
            return;
        }

        Set<String> present = options.overwrite() ? Set.of() : keysOf(target.variables().export(owner));
        List<VariableRepository.Change> pending = new ArrayList<>(rows.size());
        for (VariableRepository.Change row : rows) {
            if (present.contains(row.key())) {
                report.skipped(Category.VARIABLES);
            } else {
                pending.add(row);
            }
        }

        if (!options.dryRun() && !pending.isEmpty()) {
            target.variables().apply(pending);
        }
        pending.forEach(row -> report.migrated(Category.VARIABLES));
    }

    private static Set<String> keysOf(List<VariableRepository.Change> rows) {
        return rows.stream().map(VariableRepository.Change::key).collect(Collectors.toSet());
    }

    // Server data

    void copyServerData() {
        for (Kit kit : source.kits().findAll()) {
            copy(Category.KITS,
                    () -> target.kits().find(kit.name()).isPresent(),
                    () -> target.kits().save(kit));
        }

        for (LocationRepository.Category category : LocationRepository.Category.values()) {
            Category label = reportCategory(category);
            for (NamedLocation location : source.locations().findAll(category)) {
                copy(label,
                        () -> target.locations().find(category, location.key()).isPresent(),
                        () -> target.locations().save(category, location));
            }
        }

        copyVariables(VariableRepository.GLOBAL_OWNER);

        Set<String> bossbars = target.persistentBossbar().findAll().stream()
                .map(PersistentBossbarRepository.State::key)
                .collect(Collectors.toSet());
        for (PersistentBossbarRepository.State state : source.persistentBossbar().findAll()) {
            copy(Category.BOSSBARS,
                    () -> bossbars.contains(state.key()),
                    () -> target.persistentBossbar().save(state));
        }
    }

    // Join numbers were copied as they were, so the counter has to catch up with them
    void finish() {
        if (!options.dryRun()) {
            target.players().syncJoinSequence();
        }
    }

    // Internal

    private void copy(Category category, BooleanSupplier existsInTarget, Runnable write) {
        if (!options.overwrite() && existsInTarget.getAsBoolean()) {
            report.skipped(category);
            return;
        }
        if (!options.dryRun()) {
            write.run();
        }
        report.migrated(category);
    }

    private static Category reportCategory(LocationRepository.Category category) {
        return switch (category) {
            case WARP -> Category.WARPS;
            case SPAWN -> Category.SPAWNS;
        };
    }
}