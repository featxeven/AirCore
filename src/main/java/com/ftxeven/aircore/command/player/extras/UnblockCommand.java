package com.ftxeven.aircore.command.player.extras;

import com.ftxeven.aircore.module.extras.block.BlockHandler;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public final class UnblockCommand extends BlockActionCommand {

    private static final String KEY = "unblock";

    public UnblockCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    protected void apply(BlockHandler blocks, UUID owner, UUID target, Player initiator, Consumer<BlockHandler.Result> callback) {
        callback.accept(blocks.unblock(owner, target));
    }
}