package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class VirtualWorkstationCommand extends BaseCommand {

    private final VirtualWorkstation workstation;

    public VirtualWorkstationCommand(Context ctx, VirtualWorkstation workstation) {
        super(ctx, workstation.commandKey());
        this.workstation = workstation;
    }

    @Override
    public String permission() { return Permissions.Command.virtual(workstation.commandKey()); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        completeCooldown(sender, args);
        workstation.open(player);
    }
}