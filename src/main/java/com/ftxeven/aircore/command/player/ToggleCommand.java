package com.ftxeven.aircore.command.player;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.model.PlayerProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ToggleCommand extends BaseCommand {

    private final ToggleKey toggle;
    private final ScopeAccess access;

    public ToggleCommand(Context ctx, ToggleKey toggle) {
        super(ctx, toggle.commandKey());
        this.toggle = toggle;
        this.access = new ScopeAccess(toggle.othersPermission(), null, null);
    }

    @Override
    public String permission() { return toggle.permission(); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 0 : 1;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(0, Availability.ofPermission(sender, toggle.othersPermission()));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(toggle.othersPermission()));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String typed = args.length > 0 ? args[0] : null;
        resolveScope(sender, typed, access, scope -> {
            if (scope instanceof Scope.Single single) {
                apply(sender, single, args);
            }
        });
    }

    private void apply(CommandSender sender, Scope.Single target, String[] args) {
        PlayerProfile.Toggles current = target.profile().toggles();
        PlayerProfile.Toggles updated = toggle.with(current, !toggle.get(current));
        services().players().updateToggles(target.uuid(), updated);
        completeCooldown(sender, args);

        String suffix = toggle.get(updated) ? "enabled" : "disabled";
        announce(sender, target.uuid(), target.self(),
                toggle.langBase() + "." + suffix, toggle.langBase() + "." + suffix + "-for", toggle.langBase() + "." + suffix + "-by",
                Map.of());
    }
}