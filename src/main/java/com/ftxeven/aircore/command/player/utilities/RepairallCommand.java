package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.Optional;

public final class RepairallCommand extends LiveActionCommand {

    public RepairallCommand(Context ctx) {
        super(ctx, "repairall", new Keys("utilities.repair.inventory.self", "utilities.repair.inventory.other", "utilities.repair.inventory.by",
                "utilities.repair.inventory.all", "utilities.repair.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        return RepairSupport.repairAll(player) == 0 ? Optional.of("none-damaged") : Optional.empty();
    }
}