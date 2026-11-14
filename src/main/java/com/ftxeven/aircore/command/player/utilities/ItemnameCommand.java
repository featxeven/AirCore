package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.service.item.ItemTextService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Optional;

public final class ItemnameCommand extends ItemTextCommand {

    private static final String KEY = "itemname";
    private static final String ACTION_SET = "set";
    private static final String ACTION_RESET = "reset";

    public ItemnameCommand(Context ctx) {
        super(ctx, KEY, ItemTextService.Field.NAME);
    }

    @Override
    public int minArgs(CommandSender sender, String[] args) {
        return actionOf(args).equals(ACTION_SET) ? 2 : 1;
    }

    @Override
    public int maxArgs(CommandSender sender, String[] args) {
        return actionOf(args).equals(ACTION_RESET) ? 1 : -1;
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        switch (actionOf(args)) {
            case ACTION_SET -> set(player, args);
            case ACTION_RESET -> reset(player, args);
            default -> sendUsageError(sender, label, subLabel);
        }
    }

    private void set(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }
        Optional<ItemTextService.Verdict.Valid> name = validated(player, joinFrom(args, 1));
        if (name.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.get().component());
        item.setItemMeta(meta);

        completeCooldown(player, args);
        reply(player, "set", Map.of("name", name.get().tagged()));
    }

    private void reset(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            reply(player, "errors.not-set", Map.of());
            return;
        }
        meta.displayName(null);
        item.setItemMeta(meta);

        completeCooldown(player, args);
        reply(player, "reset", Map.of());
    }
}