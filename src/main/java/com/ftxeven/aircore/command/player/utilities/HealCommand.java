package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.Optional;

public final class HealCommand extends LiveActionCommand {

    public HealCommand(Context ctx) {
        super(ctx, "heal", new Keys("utilities.heal.self", "utilities.heal.other", "utilities.heal.by",
                "utilities.heal.all", "utilities.heal.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        if (player.getHealth() >= player.getMaxHealth() && player.getFoodLevel() >= 20 && player.getSaturation() >= 20f) {
            return Optional.of("same");
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        return Optional.empty();
    }
}