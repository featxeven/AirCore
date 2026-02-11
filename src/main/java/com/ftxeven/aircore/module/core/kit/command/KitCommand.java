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
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", "aircore.command.kit"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.config().getUsage("kit", label)));
            return true;
        }

        String kitName = args[0].toLowerCase();
        YamlConfiguration kitsConfig = manager.kits().getConfig();

        if (!kitsConfig.contains("kits." + kitName)) {
            MessageUtil.send(player, "kits.errors.not-found", Map.of());
            return true;
        }

        if (!player.hasPermission("aircore.command.kit.*") && !player.hasPermission("aircore.command.kit." + kitName)) {
            MessageUtil.send(player, "kits.usage.no-permission", Map.of("name", kitName));
            return true;
        }

        plugin.scheduler().runEntityTask(player, () -> {

            PlayerKits.KitData data = plugin.database().kits().load(player.getUniqueId(), kitName);
            long now = System.currentTimeMillis() / 1000;
            long configuredCooldown = kitsConfig.getLong("kits." + kitName + ".cooldown", 0);
            boolean oneTime = kitsConfig.getBoolean("kits." + kitName + ".one-time", false);

            long activeCooldown = data.lastCooldown() > 0 ? data.lastCooldown() : configuredCooldown;
            if (activeCooldown > 0 && now - data.lastClaim() < activeCooldown) {
                if (!player.hasPermission("aircore.bypass.kit.cooldown")) {
                    long remaining = activeCooldown - (now - data.lastClaim());
                    MessageUtil.send(player, "kits.usage.cooldown",
                            Map.of("name", kitName, "time", TimeUtil.formatSeconds(plugin, remaining)));
                    return;
                }
            }

            if (oneTime && data.oneTimeClaimed() && !player.hasPermission("aircore.bypass.kit.onetime")) {
                MessageUtil.send(player, "kits.errors.cannot-claim-again", Map.of("name", kitName));
                return;
            }

            List<Map<?, ?>> serialized = kitsConfig.getMapList("kits." + kitName + ".items");
            List<ItemStack> items = new ArrayList<>();

            for (Map<?, ?> m : serialized) {
                if (m == null) continue;

                Map<String, Object> itemData = new HashMap<>();
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        itemData.put(key, entry.getValue());
                    }
                }

                if (!itemData.isEmpty()) {
                    ItemStack stack = ItemStack.deserialize(itemData);
                    if (!stack.getType().isAir() && stack.getAmount() > 0) {
                        items.add(stack);
                    }
                }
            }

            boolean autoEquip = plugin.config().kitsAutoEquip();
            boolean dropWhenFull = plugin.config().kitsDropItemsWhenFull();
            if (!dropWhenFull && !canFitAll(player, items, autoEquip)) {
                MessageUtil.send(player, "kits.usage.inventory-full", Map.of("name", kitName));
                return;
            }

            for (ItemStack item : items) {
                ItemStack toAdd = item.clone();
                boolean equipped = false;

                if (autoEquip) {
                    EquipmentSlot slot = resolveEquipmentSlot(toAdd);
                    equipped = tryEquip(player, slot, toAdd);
                }

                if (!equipped) {
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toAdd);

                    if (!leftovers.isEmpty() && dropWhenFull) {
                        leftovers.values().forEach(l ->
                                player.getWorld().dropItemNaturally(player.getLocation(), l)
                        );
                    }
                }
            }

            plugin.database().kits().save(player.getUniqueId(), kitName, now, oneTime, configuredCooldown);

            MessageUtil.send(player, "kits.usage.claimed", Map.of("name", kitName));
        });

        return true;
    }

    private boolean tryEquip(Player player, EquipmentSlot slot, ItemStack item) {
        switch (slot) {
            case HEAD -> { if (isEmpty(player.getInventory().getHelmet())) { player.getInventory().setHelmet(item); return true; } }
            case CHEST -> { if (isEmpty(player.getInventory().getChestplate())) { player.getInventory().setChestplate(item); return true; } }
            case LEGS -> { if (isEmpty(player.getInventory().getLeggings())) { player.getInventory().setLeggings(item); return true; } }
            case FEET -> { if (isEmpty(player.getInventory().getBoots())) { player.getInventory().setBoots(item); return true; } }
            case OFF_HAND -> { if (isEmpty(player.getInventory().getItemInOffHand())) { player.getInventory().setItemInOffHand(item); return true; } }
        }
        return false;
    }

    private boolean isEmpty(ItemStack stack) { return stack == null || stack.getType().isAir(); }

    private EquipmentSlot resolveEquipmentSlot(ItemStack item) {
        EquipmentSlot api = item.getType().getEquipmentSlot();
        if (api != EquipmentSlot.HAND) return api;
        String name = item.getType().name();
        if (name.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return name.equals("SHIELD") ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
    }

    private boolean canFitAll(Player player, List<ItemStack> incoming, boolean autoEquip) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] simulated = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) simulated[i] = contents[i].clone();
        }

        for (ItemStack item : incoming) {
            ItemStack toAdd = item.clone();

            if (autoEquip) {
                EquipmentSlot slot = resolveEquipmentSlot(toAdd);
                if (slot == EquipmentSlot.HEAD && isEmpty(player.getInventory().getHelmet())) continue;
                if (slot == EquipmentSlot.CHEST && isEmpty(player.getInventory().getChestplate())) continue;
                if (slot == EquipmentSlot.LEGS && isEmpty(player.getInventory().getLeggings())) continue;
                if (slot == EquipmentSlot.FEET && isEmpty(player.getInventory().getBoots())) continue;
                if (slot == EquipmentSlot.OFF_HAND && isEmpty(player.getInventory().getItemInOffHand())) continue;
            }

            int remaining = toAdd.getAmount();
            for (ItemStack slot : simulated) {
                if (slot != null && slot.isSimilar(toAdd)) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    remaining -= Math.min(space, remaining);
                }
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                for (int i = 0; i < simulated.length; i++) {
                    if (simulated[i] == null || simulated[i].getType().isAir()) {
                        remaining -= Math.min(toAdd.getMaxStackSize(), remaining);
                        simulated[i] = toAdd.clone();
                    }
                    if (remaining <= 0) break;
                }
            }

            if (remaining > 0) return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("aircore.command.kit") || args.length != 1) return List.of();
        var section = manager.kits().getConfig().getConfigurationSection("kits");
        if (section == null) return List.of();
        return section.getKeys(false).stream()
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .filter(name -> player.hasPermission("aircore.command.kit.*") || player.hasPermission("aircore.command.kit." + name.toLowerCase()))
                .toList();
    }
}