package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
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

    protected LiveActionCommand(Context ctx, String key, Keys keys) {
        super(ctx, key);
        this.key = key;
        this.keys = keys;
        this.access = ScopeAccess.othersAndAll(key);
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
        resolveLiveScope(sender, typed, access, scope -> {
            switch (scope) {
                case LiveScope.Single single -> actOnOne(sender, single, args);
                case LiveScope.Online online -> actOnAll(sender, online.players(), args);
            }
        });
    }

    private void actOnOne(CommandSender sender, LiveScope.Single single, String[] args) {
        Player target = single.player();
        boolean self = single.self();
        Scheduler.runEntity(target, () -> {
            Optional<String> skipped = apply(target);
            if (skipped.isPresent()) {
                String errorKey = keys.errorBase() + skipped.get() + (self ? "" : "-for");
                messenger().send(sender, configs().lang().get(errorKey), self ? Map.of() : targetPlaceholders(target));
                return;
            }
            completeCooldown(sender, args);
            announce(sender, target.getUniqueId(), self, keys.self(), keys.other(), keys.by(), Map.of());
        });
    }

    private void actOnAll(CommandSender sender, List<Player> targets, String[] args) {
        for (Player online : targets) {
            Scheduler.runEntity(online, () -> {
                if (apply(online).isEmpty()) {
                    notifyTarget(sender, online.getUniqueId(), configs().lang().get(keys.by()), Map.of());
                }
            });
        }
        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get(keys.all()));
    }
}