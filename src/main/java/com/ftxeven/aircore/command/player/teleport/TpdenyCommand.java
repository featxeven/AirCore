package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.module.teleport.request.TeleportRequestHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TpdenyCommand extends BaseCommand {

    private static final String KEY = "tpdeny";
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    private final Supplier<TeleportModule> teleport;

    public TpdenyCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 0; }

    @Override
    public int maxArgs(CommandSender sender) { return 1; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        if (args.length > 0 && selectors().isAll(args[0])) {
            if (!checkPermission(player, ALL_PERMISSION)) {
                return;
            }
            denyAll(player, args);
            return;
        }

        if (args.length > 0) {
            Optional<Player> from = requireFound(resolver().onlinePlayer(args[0]), player, args[0]);
            if (from.isEmpty()) {
                return;
            }
            if (denyOne(player, from.get().getUniqueId())) {
                completeCooldown(player, args);
            }
            return;
        }

        Optional<TeleportRequestHandler.PendingRequest> mostRecent =
                teleport.get().requests().mostRecentIncoming(player.getUniqueId());
        if (mostRecent.isEmpty()) {
            messenger().send(player, configs().lang().get("teleport.tpa.errors.no-requests"));
            return;
        }
        if (denyOne(player, mostRecent.get().sender())) {
            completeCooldown(player, args);
        }
    }

    private boolean denyOne(Player player, UUID senderUuid) {
        Optional<TeleportRequestHandler.PendingRequest> denied = teleport.get().requests().deny(player.getUniqueId(), senderUuid);
        if (denied.isEmpty()) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            services().players().formatDisplayName(placeholders, "player", senderUuid);
            messenger().send(player, configs().lang().get("teleport.tpa.errors.no-request-from"), placeholders);
            return false;
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", senderUuid);
        messenger().send(player, configs().lang().get("teleport.tpa.denied.by-you"), placeholders);
        notifyTarget(player, senderUuid, configs().lang().get("teleport.tpa.denied.by-them"), Map.of());
        return true;
    }

    private void denyAll(Player player, String[] args) {
        List<TeleportRequestHandler.PendingRequest> denied = teleport.get().requests().denyAll(player.getUniqueId());
        if (denied.isEmpty()) {
            messenger().send(player, configs().lang().get("teleport.tpa.errors.no-requests"));
            return;
        }

        completeCooldown(player, args);
        for (TeleportRequestHandler.PendingRequest request : denied) {
            notifyTarget(player, request.sender(), configs().lang().get("teleport.tpa.denied.by-them"), Map.of());
        }
        messenger().send(player, configs().lang().get("teleport.tpa.denied.all"));
    }
}