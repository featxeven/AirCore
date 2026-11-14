package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.BlockService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BlockActionCommand extends BaseCommand {

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

    protected abstract void apply(BlockService blocks, UUID owner, UUID target, Player initiator, Consumer<BlockService.Result> callback);

    private BlockService blocks() {
        return services().blocks();
    }

    private static boolean showsBlockCount(BlockService.Result result) {
        return result == BlockService.Result.BLOCKED
                || result == BlockService.Result.UNBLOCKED
                || result == BlockService.Result.LIMIT_REACHED;
    }

    private void respond(Player player, BlockService.Result result, UUID targetUuid, String[] args) {
        BlockService blocks = blocks();

        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", targetUuid);

        if (showsBlockCount(result)) {
            placeholders.put("count", String.valueOf(blocks.countBlocked(player.getUniqueId())));
            placeholders.put("limit", PermissionTiers.display(blocks.maxBlocked(player), configs().lang()));
        }

        if (result == BlockService.Result.BLOCKED || result == BlockService.Result.UNBLOCKED) {
            completeCooldown(player, args);
        }

        messenger().send(player, configs().lang().get(blocks.langKeyFor(result)), placeholders);
    }
}