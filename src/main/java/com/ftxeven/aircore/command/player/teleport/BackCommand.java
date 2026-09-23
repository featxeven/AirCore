package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class BackCommand extends AbstractCommand {

    private static final String KEY = "back";

    private final Supplier<TeleportModule> teleport;
    private final ConfirmationFlow confirmations;

    public BackCommand(Context ctx, Supplier<TeleportModule> teleport, PluginGuiManager guis) {
        super(ctx, KEY);
        this.teleport = teleport;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 0; }

    @Override
    public int maxArgs(CommandSender sender) { return 0; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        Optional<Position> last = teleport.get().back().peek(player.getUniqueId());
        if (last.isEmpty()) {
            TeleportMessages.backNoLocation().send(player, configs(), messenger());
            return;
        }

        confirmations.guiOnly(player, config().gui("confirm", args),
                Map.of(), TeleportMessages.backPlaceholders(last.get()), GuiFlags.forBack(player, teleport.get()),
                () -> teleport.get().teleportBack(player, () -> completeCooldown(sender, args)));
    }
}