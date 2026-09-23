package com.ftxeven.aircore.command.player.economy;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class BalanceCommand extends AbstractCommand {

    private static final String KEY = "balance";
    private static final ScopeAccess ACCESS = ScopeAccess.othersOnly(KEY);

    private final Supplier<EconomyModule> economyModule;

    public BalanceCommand(Context ctx, Supplier<EconomyModule> economyModule) {
        super(ctx, KEY);
        this.economyModule = economyModule;
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
                completeCooldown(sender, args);
                sendBalance(sender, single);
            }
        });
    }

    private void sendBalance(CommandSender sender, Scope.Single target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        if (!target.self()) {
            services().players().formatDisplayName(placeholders, "target", target.uuid());
        }
        economyModule.get().formatter().formatInto(placeholders, "balance", target.profile().balance());
        messenger().send(sender, configs().lang().get(target.self() ? "economy.balance.self" : "economy.balance.other"), placeholders);
    }
}