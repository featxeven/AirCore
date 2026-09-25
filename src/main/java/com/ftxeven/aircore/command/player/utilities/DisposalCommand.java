package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DisposalCommand extends BaseCommand {

    private static final String KEY = "disposal";

    private final PluginGuiManager guis;

    public DisposalCommand(Context ctx, PluginGuiManager guis) {
        super(ctx, KEY);
        this.guis = guis;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        guis.openMenu((Player) sender, KEY, config().gui("menu", args).orElse(null));
    }
}