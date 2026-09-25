package com.ftxeven.aircore.command.player.homes;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.flag.FlagGate;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomeTeleporter;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Supplier;

public final class HomeCommand extends BaseCommand {

    private static final String KEY = "home";

    private final Supplier<HomesModule> homes;
    private final Supplier<TeleportModule> teleport;
    private final PluginGuiManager guis;
    private final ConfirmationFlow confirmations;

    public HomeCommand(Context ctx, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport, PluginGuiManager guis) {
        super(ctx, KEY);
        this.homes = homes;
        this.teleport = teleport;
        this.guis = guis;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        if (args.length == 0) {
            openMenuOrBrowse(player, args);
            return;
        }

        teleportNamed(player, args[0], args);
    }

    private void openMenuOrBrowse(Player player, String[] args) {
        Optional<String> menuGui = config().gui("menu", args);
        if (menuGui.isPresent() && GuiActions.guiEnabled(guis.guis(), menuGui.get())) {
            guis.guis().open(player, menuGui.get(), new LinkedHashMap<>(),
                    new OpenOptions(FlagGate.NO_FLAGS, Map.of(), OpenOptions.Kind.ENTRY, List.of()));
            return;
        }
        HomeTeleporter.browse(messenger(), configs(), services(), homes, teleport, player, () -> completeCooldown(player, args));
    }

    private void teleportNamed(Player player, String name, String[] args) {
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
                () -> HomeTeleporter.to(messenger(), configs(), services(), homes, teleport, player, home.position(),
                        "homes.teleported.self", "homes.cancelled.self", Map.of("name", home.name()),
                        () -> completeCooldown(player, args)));
    }
}