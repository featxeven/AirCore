package com.ftxeven.aircore.command.player.homes;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.homes.HomeTeleporter;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class PlayerhomeCommand extends BaseCommand {

    private static final String KEY = "playerhome";
    private static final String TELEPORT_PERMISSION = Permissions.Command.teleport(KEY);

    private final Supplier<HomesModule> homes;
    private final Supplier<TeleportModule> teleport;
    private final PluginGuiManager guis;
    private final ConfirmationFlow confirmations;
    private final HomeCommand homeCommand;

    public PlayerhomeCommand(Context ctx, Supplier<HomesModule> homes, Supplier<TeleportModule> teleport,
                             PluginGuiManager guis, HomeCommand homeCommand) {
        super(ctx, KEY);
        this.homes = homes;
        this.teleport = teleport;
        this.guis = guis;
        this.confirmations = new ConfirmationFlow(guis);
        this.homeCommand = homeCommand;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(1, Availability.ofPermission(sender, TELEPORT_PERMISSION));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(TELEPORT_PERMISSION));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String targetToken = args[0];

        if (args.length > 1 && !checkPermission(player, TELEPORT_PERMISSION)) {
            return;
        }

        if (resolver().matchesSelf(player, targetToken)) {
            String[] selfArgs = Arrays.copyOfRange(args, 1, args.length);
            homeCommand.execute(player, label, subLabel, selfArgs);
            return;
        }

        if (args.length == 1) {
            resolveTarget(player, targetToken, target -> openHomesMenu(player, target.uuid(), args));
            return;
        }

        String name = args[1];
        resolveTarget(player, targetToken, target -> teleportToNamedHome(player, target.uuid(), name, args));
    }

    // Browsing another player's homes

    private void openHomesMenu(Player player, UUID targetUuid, String[] args) {
        Scheduler.continueOn(player, homes.get().warm(targetUuid), (ignored, error) -> {
            Optional<String> guiId = config().gui("menu", args);
            if (guiId.isPresent()) {
                completeCooldown(player, args);
            }
            guis.openForTarget(player, KEY, guiId.orElse(null), targetUuid, Map.of());
        });
    }

    // Teleporting to another player's named home

    private void teleportToNamedHome(Player player, UUID targetUuid, String name, String[] args) {
        Scheduler.continueOn(player, homes.get().warm(targetUuid), (ignored, error) -> {
            Optional<Home> resolved = homes.get().find(targetUuid, name);
            if (resolved.isEmpty()) {
                Map<String, String> missing = targetPlaceholders(targetUuid);
                missing.put("name", name);
                messenger().send(player, configs().lang().get("homes.errors.not-found-for"), missing);
                return;
            }
            Home home = resolved.get();

            Map<String, String> placeholders = targetPlaceholders(targetUuid);
            placeholders.putAll(GuiPlaceholders.homeWithIcon(configs(), home));

            confirmations.guiOnly(player, config().gui("confirm", args),
                    Map.of(GuiSession.ATTR_TARGET, targetUuid, "id", home.name()),
                    placeholders, GuiFlags.forHome(player, configs().filter(), targetUuid, home, homes.get().homeResolver(targetUuid)),
                    () -> HomeTeleporter.to(messenger(), configs(), services(), homes, teleport, player, home.position(),
                            "homes.teleported.other", "homes.cancelled.other", placeholders,
                            () -> completeCooldown(player, args)));
        });
    }
}