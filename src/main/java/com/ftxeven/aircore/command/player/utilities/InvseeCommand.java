package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.inventory.InventoryKind;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class InvseeCommand extends BaseCommand {

    private static final String KEY = "invsee";
    private static final String MODIFY_PERMISSION = Permissions.Command.modify(KEY);

    private final PluginGuiManager guis;

    public InvseeCommand(Context ctx, PluginGuiManager guis) {
        super(ctx, KEY);
        this.guis = guis;
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
        Player viewer = (Player) sender;
        String typed = args[0];

        if (resolver().matchesSelf(viewer, typed)) {
            messenger().send(sender, configs().lang().get("utilities.inventory-view.invsee.errors.self"));
            return;
        }

        resolveTarget(sender, typed, target -> {
            Optional<String> guiId = config().gui("menu", args);
            if (guiId.isPresent()) {
                completeCooldown(sender, args);
            }
            boolean canModify = sender.hasPermission(MODIFY_PERMISSION);
            guis.openLiveInventory(viewer, target.uuid(), InventoryKind.MAIN, canModify, guiId.orElse(null));
        });
    }
}