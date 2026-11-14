package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.service.BlockService;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public final class BlockCommand extends BlockActionCommand {

    private static final String KEY = "block";

    public BlockCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    protected void apply(BlockService blocks, UUID owner, UUID target, Player initiator, Consumer<BlockService.Result> callback) {
        blocks.block(owner, target, initiator, callback);
    }
}