package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.Optional;

public final class RepairCommand extends LiveActionCommand {

    public RepairCommand(Context ctx) {
        super(ctx, "repair", new Keys("utilities.repair.held.self", "utilities.repair.held.other", "utilities.repair.held.by",
                "utilities.repair.held.all", "utilities.repair.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        return switch (RepairSupport.repairHeldItem(player)) {
            case REPAIRED -> Optional.empty();
            case NOT_DAMAGED -> Optional.of("not-damaged");
            case CANNOT_REPAIR -> Optional.of("cannot-repair");
            case NO_ITEM -> Optional.of("no-item");
        };
    }
}