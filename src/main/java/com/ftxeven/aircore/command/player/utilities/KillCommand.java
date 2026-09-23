package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.Optional;

public final class KillCommand extends LiveActionCommand {

    public KillCommand(Context ctx) {
        super(ctx, "kill", new Keys("utilities.kill.self", "utilities.kill.other", "utilities.kill.by",
                "utilities.kill.all", "utilities.kill.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        player.setHealth(0.0);
        return Optional.empty();
    }
}