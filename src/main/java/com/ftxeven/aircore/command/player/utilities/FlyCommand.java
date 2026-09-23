package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class FlyCommand extends AbstractCommand {

    private static final String KEY = "fly";
    private static final ScopeAccess ACCESS = ScopeAccess.othersOnly(KEY);

    public FlyCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 0 : 1;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(0, Availability.ofPermission(sender, ACCESS.others()));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(ACCESS.others()));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String typed = args.length > 0 ? args[0] : null;
        resolveScope(sender, typed, ACCESS, scope -> {
            if (scope instanceof Scope.Single single) {
                apply(sender, single, args);
            }
        });
    }

    private void apply(CommandSender sender, Scope.Single target, String[] args) {
        boolean enabled = !target.profile().flight().allowed();
        runOnOwner(target, online -> {
            if (enabled && online.isPresent()
                    && blockedByWorldRestriction(sender, online.get(), target.self(), MainConfig.RestrictedFeature.FLY)) {
                return;
            }
            services().players().updateFlight(target.uuid(), new PlayerProfile.Flight(enabled, enabled));
            online.ifPresent(player -> {
                player.setAllowFlight(enabled);
                player.setFlying(enabled);
            });
            completeCooldown(sender, args);

            String suffix = enabled ? "enabled" : "disabled";
            announce(sender, target.uuid(), target.self(),
                    "utilities.fly." + suffix, "utilities.fly." + suffix + "-for", "utilities.fly." + suffix + "-by", Map.of());
        });
    }
}