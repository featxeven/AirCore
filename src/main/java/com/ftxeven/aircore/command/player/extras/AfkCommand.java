package com.ftxeven.aircore.command.player.extras;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.module.extras.afk.AfkHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class AfkCommand extends AbstractCommand {

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
        AfkHandler afk = modules().extras().afk();

        String reason = null;
        if (!afk.isAfk(player.getUniqueId())) {
            reason = joinReason(args);
            if (reason != null && !acceptReason(afk, player, reason)) {
                return;
            }
        }

        AfkHandler.ToggleResult result = afk.toggle(player, reason);
        if (result instanceof AfkHandler.ToggleResult.Restricted(String world)) {
            messenger().send(player, configs().lang().get("errors.general.world-restricted"), Map.of("world", world));
            return;
        }
        completeCooldown(sender, args);
    }

    // Reason handling

    private boolean reasonEnabled() {
        return configs().extras().afk().reason().enabled();
    }

    private @Nullable String joinReason(String[] args) {
        if (!reasonEnabled() || args.length == 0) {
            return null;
        }
        String joined = String.join(" ", args).strip();
        return joined.isEmpty() ? null : joined;
    }

    // tells the player why when the reason is rejected
    private boolean acceptReason(AfkHandler afk, Player player, String reason) {
        return switch (afk.validateReason(player, reason)) {
            case AfkHandler.ReasonVerdict.Accepted ignored -> true;
            case AfkHandler.ReasonVerdict.TooLong(int length, int max) -> {
                messenger().send(player, configs().lang().get("extras.afk.errors.too-long"), Map.of(
                        "length", String.valueOf(length),
                        "max", String.valueOf(max)
                ));
                yield false;
            }
            case AfkHandler.ReasonVerdict.Disallowed ignored -> {
                messenger().send(player, configs().lang().get("extras.afk.errors.profanity"));
                yield false;
            }
        };
    }
}