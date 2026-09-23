package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.Optional;

public final class FeedCommand extends LiveActionCommand {

    public FeedCommand(Context ctx) {
        super(ctx, "feed", new Keys("utilities.feed.self", "utilities.feed.other", "utilities.feed.by",
                "utilities.feed.all", "utilities.feed.errors."));
    }

    @Override
    protected Optional<String> apply(Player player) {
        if (player.getFoodLevel() >= 20 && player.getSaturation() >= 20f) {
            return Optional.of("same");
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        return Optional.empty();
    }
}