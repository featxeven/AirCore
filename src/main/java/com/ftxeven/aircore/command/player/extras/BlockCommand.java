package com.ftxeven.aircore.command.player.extras;

import com.ftxeven.aircore.module.extras.block.BlockHandler;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public final class BlockCommand extends BlockActionCommand {

    private static final String KEY = "block";

    public BlockCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    protected void apply(BlockHandler blocks, UUID owner, UUID target, Player initiator, Consumer<BlockHandler.Result> callback) {
        blocks.block(owner, target, initiator, callback);
    }
}