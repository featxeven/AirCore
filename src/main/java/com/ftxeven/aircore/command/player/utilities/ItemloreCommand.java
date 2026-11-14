package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.service.item.ItemTextService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class ItemloreCommand extends ItemTextCommand {

    private static final String KEY = "itemlore";
    private static final String ACTION_ADD = "add";
    private static final String ACTION_SET = "set";
    private static final String ACTION_REMOVE = "remove";
    private static final String ACTION_CLEAR = "clear";

    public ItemloreCommand(Context ctx) {
        super(ctx, KEY, ItemTextService.Field.LORE);
    }

    @Override
    public int minArgs(CommandSender sender, String[] args) {
        return switch (actionOf(args)) {
            case ACTION_SET -> 3;
            case ACTION_ADD, ACTION_REMOVE -> 2;
            default -> 1;
        };
    }

    @Override
    public int maxArgs(CommandSender sender, String[] args) {
        return switch (actionOf(args)) {
            case ACTION_CLEAR -> 1;
            case ACTION_REMOVE -> 2;
            default -> -1;
        };
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        switch (actionOf(args)) {
            case ACTION_ADD -> add(player, args);
            case ACTION_SET -> set(player, args);
            case ACTION_REMOVE -> remove(player, args);
            case ACTION_CLEAR -> clear(player, args);
            default -> sendUsageError(sender, label, subLabel);
        }
    }

    private void add(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = lore(meta);

        int limit = configs().main().utilityDefaults().itemLore().maxLines();
        if (limit >= 0 && lore.size() >= limit) {
            reply(player, "errors.limit-reached", Map.of(
                    "count", String.valueOf(lore.size()),
                    "limit", String.valueOf(limit)
            ));
            return;
        }

        Optional<ItemTextService.Verdict.Valid> line = validated(player, joinFrom(args, 1));
        if (line.isEmpty()) {
            return;
        }

        lore.add(line.get().component());
        store(item, meta, lore);

        completeCooldown(player, args);
        reply(player, "added", Map.of("line", String.valueOf(lore.size())));
    }

    private void set(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = lore(meta);

        OptionalInt number = resolveLine(player, args[1], lore);
        if (number.isEmpty()) {
            return;
        }
        Optional<ItemTextService.Verdict.Valid> line = validated(player, joinFrom(args, 2));
        if (line.isEmpty()) {
            return;
        }

        lore.set(number.getAsInt() - 1, line.get().component());
        store(item, meta, lore);

        completeCooldown(player, args);
        reply(player, "set", Map.of("line", String.valueOf(number.getAsInt())));
    }

    private void remove(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = lore(meta);

        OptionalInt number = resolveLine(player, args[1], lore);
        if (number.isEmpty()) {
            return;
        }

        lore.remove(number.getAsInt() - 1);
        store(item, meta, lore);

        completeCooldown(player, args);
        reply(player, "removed", Map.of("line", String.valueOf(number.getAsInt())));
    }

    private void clear(Player player, String[] args) {
        Optional<ItemStack> held = requireHeldItem(player);
        if (held.isEmpty()) {
            return;
        }

        ItemStack item = held.get();
        ItemMeta meta = item.getItemMeta();
        if (lore(meta).isEmpty()) {
            reply(player, "errors.no-lore", Map.of());
            return;
        }
        store(item, meta, List.of());

        completeCooldown(player, args);
        reply(player, "cleared", Map.of());
    }

    // Lore helpers

    private static List<Component> lore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    private static void store(ItemStack item, ItemMeta meta, List<Component> lore) {
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    // the 1-based line the player typed; empty once they've been told it isn't a line of this lore
    private OptionalInt resolveLine(Player player, String typed, List<Component> lore) {
        if (lore.isEmpty()) {
            reply(player, "errors.no-lore", Map.of());
            return OptionalInt.empty();
        }

        OptionalInt line = parseLine(typed);
        if (line.isPresent() && line.getAsInt() >= 1 && line.getAsInt() <= lore.size()) {
            return line;
        }
        reply(player, "errors.invalid-line", Map.of(
                "line", escapeUserInput(typed),
                "count", String.valueOf(lore.size())
        ));
        return OptionalInt.empty();
    }

    private static OptionalInt parseLine(String typed) {
        try {
            return OptionalInt.of(Integer.parseInt(typed));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}