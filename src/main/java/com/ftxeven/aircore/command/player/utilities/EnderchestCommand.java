package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class EnderchestCommand extends BaseCommand {

    private static final String KEY = "enderchest";

    private final PluginGuiManager guis;

    public EnderchestCommand(Context ctx, PluginGuiManager guis) {
        super(ctx, KEY);
        this.guis = guis;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        Optional<String> guiId = config().gui("menu", args);
        if (guiId.isPresent()) {
            completeCooldown(sender, args);
        }
        guis.openOwnEnderChest(player, guiId.orElse(null));
    }
}