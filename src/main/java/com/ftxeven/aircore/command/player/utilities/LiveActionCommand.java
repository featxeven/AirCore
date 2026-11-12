package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The shape shared by /heal, /feed, /repair, /repairall, /clearinventory and /kill
 */
public abstract class LiveActionCommand extends BaseCommand {

    /** lang keys for one action. {@code errorBase} is prefixed to the reason a target was skipped. */
    public record Keys(String self, String other, String by, String all, String errorBase) {}

    private final String key;
    private final Keys keys;
    private final ScopeAccess access;
    private final LiveActionRunner runner;

    protected LiveActionCommand(Context ctx, String key, Keys keys) {
        super(ctx, key);
        this.key = key;
        this.keys = keys;
        this.access = ScopeAccess.othersAndAll(key);
        this.runner = new LiveActionRunner(ctx.configs(), ctx.messenger(), ctx.feedback());
    }

    /** runs on the target's own thread. Empty = done; otherwise the reason (lang suffix) the target was skipped */
    protected abstract Optional<String> apply(Player target);

    @Override
    public String permission() { return Permissions.Command.of(key); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 0 : 1;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(0, Availability.ofConfig(access.canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), access.canTargetOthers(sender));
    }

    @Override
    public final void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String typed = args.length > 0 ? args[0] : null;
        resolveLiveScope(sender, typed, access, scope -> onResolved(sender, scope, args));
    }

    protected void onResolved(CommandSender sender, LiveScope scope, String[] args) {
        act(sender, scope, args);
    }

    protected final void act(CommandSender sender, LiveScope scope, String[] args) {
        runner.run(sender, scope, keys, this::apply, () -> completeCooldown(sender, args));
    }
}