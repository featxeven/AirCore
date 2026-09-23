package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.TeleportType;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class TpaCommand extends AbstractCommand {

    private static final String KEY = "tpa";
    private static final String CONFIRMATION_TYPE = "tpa";
    private static final String SENT_TO_SELF = "teleport.tpa.sent-to";
    private static final String RECEIVED_BY_TARGET = "teleport.tpa.received-from";

    private final Supplier<TeleportModule> teleport;
    private final ConfirmationFlow confirmations;

    public TpaCommand(Context ctx, Supplier<TeleportModule> teleport, PluginGuiManager guis) {
        super(ctx, KEY);
        this.teleport = teleport;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 1; }

    @Override
    public int maxArgs(CommandSender sender) { return 1; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        Optional<Player> target = requireFound(resolver().onlinePlayer(args[0]), sender, args[0]);
        if (target.isEmpty()) {
            return;
        }
        proceed(player, target.get(), args);
    }

    // Confirmation flow

    private void proceed(Player sender, Player target, String[] args) {
        confirmations.gated(ctx, sender, CONFIRMATION_TYPE, target.getUniqueId(),
                teleport.get().sendConfirmationRequired(sender),
                () -> previewBlocked(sender, target, args),
                config().gui("confirm", args),
                Map.of("target", target.getName()),
                requestPlaceholders(target),
                GuiFlags.builder(sender).online(target.getUniqueId()).build(),
                configs().teleport().requests().confirmationTimeout(),
                "teleport.tpa.confirmation.to.request", "teleport.tpa.confirmation.to.expired",
                () -> send(sender, target, args));
    }

    private Optional<Runnable> previewBlocked(Player sender, Player target, String[] args) {
        return teleport.get().previewRequest(sender, target)
                .map(result -> (Runnable) () -> TeleportMessages.notifySendResult(messenger(), configs(), services(), teleport.get(),
                        sender, target, result, SENT_TO_SELF, RECEIVED_BY_TARGET, () -> completeCooldown(sender, args)));
    }

    // carries both %player% (chat confirmation/expiry messages) and %target% (confirm GUI)
    private Map<String, String> requestPlaceholders(Player target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", target.getUniqueId());
        services().players().formatDisplayName(placeholders, "target", target.getUniqueId());
        return placeholders;
    }

    private void send(Player sender, Player target, String[] args) {
        TeleportMessages.sendTeleportRequest(messenger(), configs(), services(), teleport.get(), sender, target, TeleportType.TPA,
                SENT_TO_SELF, RECEIVED_BY_TARGET, () -> completeCooldown(sender, args));
    }
}