package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.render.GuiFlags;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClearinventoryCommand extends LiveActionCommand {

    public static final String KEY = "clearinventory";

    public static final Keys KEYS = new Keys("utilities.clear-inventory.self", "utilities.clear-inventory.other",
            "utilities.clear-inventory.by", "utilities.clear-inventory.all", "utilities.clear-inventory.errors.");

    private final ConfirmationFlow confirmations;

    public ClearinventoryCommand(Context ctx, PluginGuiManager guis) {
        super(ctx, KEY, KEYS);
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    protected Optional<String> apply(Player target) {
        return clear(target);
    }

    public static Optional<String> clear(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isEmpty(inventory.getStorageContents()) && isEmpty(inventory.getArmorContents()) && isAir(inventory.getItemInOffHand())) {
            return Optional.of("already-empty");
        }
        inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
        inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        inventory.setItemInOffHand(null);
        return Optional.empty();
    }

    // Confirmation

    @Override
    protected void onResolved(CommandSender sender, LiveScope scope, String[] args) {
        if (!(sender instanceof Player viewer)) {
            act(sender, scope, args);
            return;
        }
        switch (scope) {
            case LiveScope.Single single -> confirmOne(viewer, single, args);
            case LiveScope.Online online -> confirmAll(viewer, online, args);
        }
    }

    private void confirmOne(Player viewer, LiveScope.Single single, String[] args) {
        Player target = single.player();
        UUID targetUuid = target.getUniqueId();
        Map<String, Object> attributes = single.self() ? Map.of() : Map.of("target", target.getName());

        confirmations.guiOnly(viewer, config().gui("confirm", args),
                attributes,
                targetPlaceholders(target),
                GuiFlags.builder(viewer).online(targetUuid).self(targetUuid).build(),
                () -> act(viewer, single, args));
    }

    private void confirmAll(Player viewer, LiveScope.Online online, String[] args) {
        confirmations.guiOnly(viewer, config().gui("confirm-all", args),
                Map.of(),
                Map.of(),
                GuiFlags.builder(viewer).build(),
                () -> act(viewer, online, args));
    }

    private static boolean isEmpty(ItemStack[] items) {
        for (ItemStack item : items) {
            if (!isAir(item)) return false;
        }
        return true;
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}