package com.ftxeven.aircore.command.player.extras;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BlockActionCommand extends AbstractCommand {

    private final String key;

    protected BlockActionCommand(Context ctx, String key) {
        super(ctx, key);
        this.key = key;
    }

    @Override
    public String permission() {
        return Permissions.Command.of(key);
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 1;
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        resolveTarget(sender, args[0], target -> {
            UUID targetUuid = target.uuid();
            apply(blocks(), player.getUniqueId(), targetUuid, player,
                    result -> respond(player, result, targetUuid, args));
        });
    }

    protected abstract void apply(BlockHandler blocks, UUID owner, UUID target, Player initiator, Consumer<BlockHandler.Result> callback);

    private BlockHandler blocks() {
        return modules().extras().blocks();
    }

    private static boolean showsBlockCount(BlockHandler.Result result) {
        return result == BlockHandler.Result.BLOCKED
                || result == BlockHandler.Result.UNBLOCKED
                || result == BlockHandler.Result.LIMIT_REACHED;
    }

    private void respond(Player player, BlockHandler.Result result, UUID targetUuid, String[] args) {
        BlockHandler blocks = blocks();

        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", targetUuid);

        if (showsBlockCount(result)) {
            placeholders.put("count", String.valueOf(blocks.countBlocked(player.getUniqueId())));
            placeholders.put("limit", PermissionTiers.display(blocks.maxBlocked(player), configs().lang()));
        }

        if (result == BlockHandler.Result.BLOCKED || result == BlockHandler.Result.UNBLOCKED) {
            completeCooldown(player, args);
        }

        messenger().send(player, configs().lang().get(blocks.langKeyFor(result)), placeholders);
    }
}