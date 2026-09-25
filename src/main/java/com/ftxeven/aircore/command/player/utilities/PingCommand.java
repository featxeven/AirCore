package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PingCommand extends BaseCommand {

    private static final String KEY = "ping";
    private static final ScopeAccess ACCESS = ScopeAccess.othersOnly(KEY);

    public PingCommand(Context ctx) {
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
        resolveLiveScope(sender, typed, ACCESS, scope -> {
            if (scope instanceof LiveScope.Single single) {
                completeCooldown(sender, args);
                sendPing(sender, single.player(), single.self());
            }
        });
    }

    private void sendPing(CommandSender sender, Player target, boolean self) {
        if (self) {
            messenger().send(sender, configs().lang().get("utilities.ping.self"), Map.of("ping", String.valueOf(target.getPing())));
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", target);
        placeholders.put("ping", String.valueOf(target.getPing()));
        messenger().send(sender, configs().lang().get("utilities.ping.other"), placeholders);
    }
}