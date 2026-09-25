package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.module.teleport.TeleportMessages;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class DeletewarpCommand extends BaseCommand {

    private static final String KEY = "deletewarp";

    private final Supplier<TeleportModule> teleport;

    public DeletewarpCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<NamedLocation> warp = teleport.get().findWarp(args[0]);
        if (warp.isEmpty() || !teleport.get().deleteWarp(warp.get().key())) {
            messenger().send(sender, configs().lang().get("teleport.warps.errors.not-found"), Map.of("name", escapeUserInput(args[0])));
            return;
        }

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("teleport.warps.deleted"), TeleportMessages.warpPlaceholders(warp.get()));
    }
}