package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.module.afk.AfkModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class AfkCommand extends BaseCommand {

    private static final String KEY = "afk";

    public AfkCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int maxArgs() { return reasonEnabled() ? -1 : 0; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), reasonEnabled());
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        AfkModule afk = modules().afk();

        String reason = null;
        if (!afk.isAfk(player.getUniqueId())) {
            reason = joinReason(args);
            if (reason != null && !acceptReason(afk, player, reason)) {
                return;
            }
        }

        AfkModule.ToggleResult result = afk.toggle(player, reason);
        if (result instanceof AfkModule.ToggleResult.Restricted(String world)) {
            messenger().send(player, configs().lang().get("errors.general.world-restricted"), Map.of("world", world));
            return;
        }
        completeCooldown(sender, args);
    }

    // Reason handling

    private boolean reasonEnabled() {
        return configs().afk().reason().enabled();
    }

    private @Nullable String joinReason(String[] args) {
        if (!reasonEnabled() || args.length == 0) {
            return null;
        }
        String joined = String.join(" ", args).strip();
        return joined.isEmpty() ? null : joined;
    }

    // tells the player why when the reason is rejected
    private boolean acceptReason(AfkModule afk, Player player, String reason) {
        return switch (afk.validateReason(player, reason)) {
            case AfkModule.ReasonVerdict.Accepted ignored -> true;
            case AfkModule.ReasonVerdict.TooLong(int length, int max) -> {
                messenger().send(player, configs().lang().get("afk.errors.too-long"), Map.of(
                        "length", String.valueOf(length),
                        "max", String.valueOf(max)
                ));
                yield false;
            }
            case AfkModule.ReasonVerdict.Disallowed ignored -> {
                messenger().send(player, configs().lang().get("afk.errors.profanity"));
                yield false;
            }
        };
    }
}