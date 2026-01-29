package com.ftxeven.aircore.module.core.kit.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.core.kit.KitManager;
import com.ftxeven.aircore.database.player.PlayerKits;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class KitCommand implements TabExecutor {

    private final AirCore plugin;
    private final KitManager manager;

    public KitCommand(AirCore plugin, KitManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission("aircore.command.kit")) {
            MessageUtil.send(player, "errors.no-permission",
                    Map.of("permission", "aircore.command.kit"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage",
                    Map.of("usage", plugin.config().getUsage("kit", label)));
            return true;
        }

        String kitName = args[0].toLowerCase();

        YamlConfiguration kitsConfig = manager.kits().getConfig();
        if (!kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.kit.*")
                && !player.hasPermission("aircore.command.kit." + kitName)) {
            MessageUtil.send(player, "kits.usage.no-permission", Map.of("name", kitName));
            return true;
        }

        long configuredCooldown = kitsConfig.getLong("kits." + kitName + ".cooldown", 0);
        boolean oneTime = kitsConfig.getBoolean("kits." + kitName + ".one-time", false);

        long now = System.currentTimeMillis() / 1000;
        PlayerKits.KitData data = plugin.database().kits().load(player.getUniqueId(), kitName);

        // Cooldown check
        long activeCooldown = data.lastCooldown() > 0 ? data.lastCooldown() : configuredCooldown;
        if (activeCooldown > 0 && now - data.lastClaim() < activeCooldown) {
            if (!player.hasPermission("aircore.bypass.kit.cooldown")) {
                long remaining = activeCooldown - (now - data.lastClaim());
                String timeStr = TimeUtil.formatSeconds(plugin, remaining);
                MessageUtil.send(player, "kits.usage.cooldown",
                        Map.of("name", kitName, "time", timeStr));
                return true;
            }
        }

        // One-time check
        if (oneTime && data.oneTimeClaimed()) {
            if (!player.hasPermission("aircore.bypass.kit.onetime")) {
                MessageUtil.send(player, "kits.errors.cannot-claim-again",
                        Map.of("name", kitName));
                return true;
            }
        }

        List<Map<?, ?>> serialized = kitsConfig.getMapList("kits." + kitName + ".items");
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> m : serialized) {
            if (m != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) m;
                ItemStack stack = ItemStack.deserialize(new HashMap<>(casted));
                if (!stack.getType().isAir() && stack.getAmount() > 0) {
                    items.add(stack);
                }
            }
        }

        boolean autoEquip = plugin.config().kitsAutoEquip();
        boolean dropWhenFull = plugin.config().kitsDropItemsWhenFull();

        // Inventory pre-check
        if (!dropWhenFull) {
            if (!canFitAll(player, items, autoEquip)) {
                MessageUtil.send(player, "kits.usage.inventory-full", Map.of("name", kitName));
                return true;
            }
        }

        plugin.scheduler().runEntityTask(player, () -> {
            for (ItemStack item : items) {
                boolean equipped = false;

                if (autoEquip) {
                    EquipmentSlot slot = resolveEquipmentSlot(item);
                    switch (slot) {
                        case HEAD -> {
                            if (isEmpty(player.getInventory().getHelmet())) {
                                player.getInventory().setHelmet(item);
                                equipped = true;
                            }
                        }
                        case CHEST -> {
                            if (isEmpty(player.getInventory().getChestplate())) {
                                player.getInventory().setChestplate(item);
                                equipped = true;
                            }
                        }
                        case LEGS -> {
                            if (isEmpty(player.getInventory().getLeggings())) {
                                player.getInventory().setLeggings(item);
                                equipped = true;
                            }
                        }
                        case FEET -> {
                            if (isEmpty(player.getInventory().getBoots())) {
                                player.getInventory().setBoots(item);
                                equipped = true;
                            }
                        }
                        case OFF_HAND -> {
                            if (isEmpty(player.getInventory().getItemInOffHand())) {
                                player.getInventory().setItemInOffHand(item);
                                equipped = true;
                            }
                        }
                        default -> { /* not auto-equipable */ }
                    }
                }

                if (!equipped) {
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                    if (!leftovers.isEmpty()) {
                        if (dropWhenFull) {
                            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                        }
                    }
                }
            }
        });

        plugin.scheduler().runAsync(() ->
                plugin.database().kits().save(player.getUniqueId(), kitName, now, oneTime, configuredCooldown)
        );

        MessageUtil.send(player, "kits.usage.claimed", Map.of("name", kitName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (!player.hasPermission("aircore.command.kit")) return List.of();

        if (args.length == 1) {
            var section = manager.kits().getConfig().getConfigurationSection("kits");
            if (section == null) return List.of();

            return section.getKeys(false).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .filter(name ->
                            player.hasPermission("aircore.command.kit.*") ||
                                    player.hasPermission("aircore.command.kit." + name.toLowerCase()))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private EquipmentSlot resolveEquipmentSlot(ItemStack item) {
        EquipmentSlot api = item.getType().getEquipmentSlot();
        if (api != EquipmentSlot.HAND) return api;

        String name = item.getType().name();
        if (name.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        if (name.equals("SHIELD")) return EquipmentSlot.OFF_HAND;

        return EquipmentSlot.HAND;
    }

    private boolean canFitAll(Player player, List<ItemStack> incoming, boolean autoEquip) {
        ItemStack vHead = safeClone(player.getInventory().getHelmet());
        ItemStack vChest = safeClone(player.getInventory().getChestplate());
        ItemStack vLegs = safeClone(player.getInventory().getLeggings());
        ItemStack vFeet = safeClone(player.getInventory().getBoots());
        ItemStack vOff = safeClone(player.getInventory().getItemInOffHand());

        ItemStack[] contents = player.getInventory().getStorageContents().clone();

        List<ItemStack> work = new ArrayList<>();
        for (ItemStack is : incoming) work.add(is.clone());

        if (autoEquip) {
            for (ItemStack item : work) {
                EquipmentSlot slot = resolveEquipmentSlot(item);
                switch (slot) {
                    case HEAD -> {
                        if (isEmpty(vHead) && item.getAmount() > 0) {
                            vHead = takeOne(item);
                        }
                    }
                    case CHEST -> {
                        if (isEmpty(vChest) && item.getAmount() > 0) {
                            vChest = takeOne(item);
                        }
                    }
                    case LEGS -> {
                        if (isEmpty(vLegs) && item.getAmount() > 0) {
                            vLegs = takeOne(item);
                        }
                    }
                    case FEET -> {
                        if (isEmpty(vFeet) && item.getAmount() > 0) {
                            vFeet = takeOne(item);
                        }
                    }
                    case OFF_HAND -> {
                        if (isEmpty(vOff) && item.getAmount() > 0) {
                            vOff = takeOne(item);
                        }
                    }
                    default -> { /* HAND > not auto-equipable */ }
                }
            }
        }

        // Place remaining amounts into inventory
        for (ItemStack item : work) {
            if (item.getAmount() <= 0) continue;

            int remaining = item.getAmount();

            // Fill partial stacks
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot != null && !slot.getType().isAir() && slot.isSimilar(item)) {
                    int max = slot.getMaxStackSize();
                    int space = Math.max(0, max - slot.getAmount());
                    if (space > 0) {
                        int move = Math.min(space, remaining);
                        slot.setAmount(slot.getAmount() + move);
                        remaining -= move;
                    }
                }
            }

            // Use empty slots
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot == null || slot.getType().isAir()) {
                    int max = item.getMaxStackSize();
                    int move = Math.min(max, remaining);
                    ItemStack placed = item.clone();
                    placed.setAmount(move);
                    contents[i] = placed;
                    remaining -= move;
                }
            }

            if (remaining > 0) {
                // Not enough space to fit this item fully > abort
                return false;
            }
        }

        return true;
    }

    private ItemStack safeClone(ItemStack s) {
        return (s == null || s.getType().isAir()) ? null : s.clone();
    }

    private ItemStack takeOne(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        return one;
    }
}