package com.ftxeven.aircore.module.core.economy.service;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.block.ShulkerBox;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ItemWorthService {

    private final AirCore plugin;
    private final Map<String, Double> worthMap = new HashMap<>();
    private final Map<String, Double> enchantmentModifiers = new HashMap<>();
    private double minDurabilityPercent;
    private static final int MAX_RECURSION_DEPTH = 5;

    public ItemWorthService(AirCore plugin) {
        this.plugin = plugin;
        loadWorthFile();
        loadModifiersFile();
    }

    private void loadWorthFile() {
        worthMap.clear();

        File worthDir = new File(plugin.getDataFolder(), "data/worth");
        if (!worthDir.exists()) {
            worthDir.mkdirs();
        }

        File worthFile = new File(worthDir, "items.yml");
        if (!worthFile.exists()) {
            plugin.saveResource("data/worth/items.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worthFile);

        for (String key : yaml.getKeys(true)) {
            if (yaml.isDouble(key) || yaml.isInt(key)) {
                double value = yaml.getDouble(key);
                worthMap.put(key.toLowerCase(), value);
            }
        }
    }

    private void loadModifiersFile() {
        enchantmentModifiers.clear();

        File worthDir = new File(plugin.getDataFolder(), "data/worth");
        if (!worthDir.exists()) {
            worthDir.mkdirs();
        }

        File modifiersFile = new File(worthDir, "modifiers.yml");
        if (!modifiersFile.exists()) {
            plugin.saveResource("data/worth/modifiers.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(modifiersFile);

        if (yaml.contains("enchantments")) {
            var enchSection = yaml.getConfigurationSection("enchantments");
            if (enchSection != null) {
                for (String key : enchSection.getKeys(false)) {
                    enchantmentModifiers.put(key.toLowerCase(), yaml.getDouble("enchantments." + key));
                }
            }
        }

        minDurabilityPercent = yaml.getDouble("durability.min-worth-percent", 0.10);
    }

    public void reload() {
        loadWorthFile();
        loadModifiersFile();
    }

    public double getWorth(Material material) {
        return getWorth(material.name().toLowerCase());
    }

    public double getWorth(String itemKey) {
        return worthMap.getOrDefault(itemKey.toLowerCase(), 0.0);
    }

    public double getWorth(ItemStack item) {
        return getWorth(item, 0);
    }

    private double getWorth(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR) return 0.0;
        if (depth > MAX_RECURSION_DEPTH) return 0.0;

        ItemMeta meta = item.getItemMeta();

        // Generalized container handling
        double containerContentsWorth = handleContainer(meta, depth);
        if (containerContentsWorth >= 0) {
            double containerBase = getWorth(item.getType());
            return containerBase + containerContentsWorth;
        }

        // Normal item handling
        double baseWorth = getWorth(item.getType());
        if (baseWorth <= 0) return 0.0;

        if (meta != null) {
            baseWorth = applyEnchantmentModifier(baseWorth, meta);
            baseWorth = applyDamageModifier(baseWorth, item);
        }

        return baseWorth;
    }

    private double handleContainer(ItemMeta meta, int depth) {
        if (meta instanceof BlockStateMeta blockStateMeta) {
            var state = blockStateMeta.getBlockState();
            if (state instanceof ShulkerBox shulkerBox) {
                return sumContents(shulkerBox.getInventory().getContents(), depth);
            }
        }

        if (meta instanceof BundleMeta bundleMeta) {
            return sumContents(bundleMeta.getItems(), depth);
        }

        return -1;
    }

    private double sumContents(ItemStack[] contents, int depth) {
        double total = 0.0;
        for (ItemStack content : contents) {
            if (content == null || content.getType() == Material.AIR) continue;
            double perContentWorth = getWorth(content, depth + 1);
            if (perContentWorth <= 0) continue;
            total += perContentWorth * content.getAmount();
        }
        return total;
    }

    private double sumContents(java.util.List<ItemStack> contents, int depth) {
        return sumContents(contents.toArray(new ItemStack[0]), depth);
    }

    private double applyEnchantmentModifier(double baseWorth, ItemMeta meta) {
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (meta instanceof EnchantmentStorageMeta enchantMeta) {
            enchants = enchantMeta.getStoredEnchants();
        }

        if (enchants.isEmpty()) {
            return baseWorth;
        }

        double modifier = 1.0;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();

            String enchantKey = ench.getKey().getKey().toLowerCase();
            double perLevel = enchantmentModifiers.getOrDefault(enchantKey, 0.08);
            modifier += perLevel * level;
        }

        return baseWorth * modifier;
    }

    private double applyDamageModifier(double baseWorth, ItemStack item) {
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability > 0) {
            int damage = 0;
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable dmgMeta) {
                damage = dmgMeta.getDamage();
            }
            int currentDurability = Math.max(0, maxDurability - damage);
            double durabilityPercent = (double) currentDurability / (double) maxDurability;

            double minWorth = baseWorth * minDurabilityPercent;
            return Math.max(minWorth, baseWorth * durabilityPercent);
        }

        return baseWorth;
    }
}