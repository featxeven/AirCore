package com.ftxeven.aircore.command.player.homes;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class DeletehomeCommand extends AbstractCommand {

    private static final String KEY = "deletehome";

    private final Supplier<HomesModule> homes;
    private final ConfirmationFlow confirmations;

    public DeletehomeCommand(Context ctx, Supplier<HomesModule> homes, PluginGuiManager guis) {
        super(ctx, KEY);
        this.homes = homes;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String name = args[0];

        Optional<Home> resolved = homes.get().find(player.getUniqueId(), name);
        if (resolved.isEmpty()) {
            messenger().send(player, configs().lang().get("homes.errors.not-found"), Map.of("name", name));
            return;
        }
        Home home = resolved.get();
        UUID owner = player.getUniqueId();

        confirmations.guiOnly(player, config().gui("confirm", args),
                Map.of("id", home.name()), GuiPlaceholders.homeWithIcon(configs(), home),
                GuiFlags.forHome(player, configs().filter(), owner, home, homes.get().homeResolver(owner)),
                () -> applyDelete(player, home, args));
    }

    private void applyDelete(Player player, Home home, String[] args) {
        homes.get().delete(player.getUniqueId(), home.name());
        completeCooldown(player, args);
        messenger().send(player, configs().lang().get("homes.deleted.self"), Map.of("name", home.name()));
    }
}