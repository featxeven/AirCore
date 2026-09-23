package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SetwarpCommand extends AbstractCommand {

    private static final String KEY = "setwarp";

    private final Supplier<TeleportModule> teleport;

    public SetwarpCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
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

        Optional<NamedLocation> existing = teleport.get().findWarp(name);
        if (existing.isPresent()) {
            messenger().send(sender, configs().lang().get("teleport.warps.errors.already-exists"),
                    TeleportMessages.warpPlaceholders(existing.get()));
            return;
        }

        teleport.get().saveWarp(name, Positions.of(player.getLocation()), player.getUniqueId());

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("teleport.warps.created"), Map.of("name", escapeUserInput(name)));
    }
}