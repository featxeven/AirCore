package com.ftxeven.aircore.module.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemComponent {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemComponent(Material material) {
        if (material == null || material == Material.AIR) {
            this.item = new ItemStack(Material.AIR);
            this.meta = null;
        } else {
            this.item = new ItemStack(material);
            this.meta = item.getItemMeta();
        }
    }

    public ItemComponent amount(int amount) {
        if (meta != null) {
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        }
        return this;
    }

    public ItemComponent name(Component name) {
        if (meta != null && name != null) meta.displayName(name);
        return this;
    }

    public ItemComponent lore(List<Component> lore) {
        if (meta != null && lore != null && !lore.isEmpty()) meta.lore(lore);
        return this;
    }

    public ItemComponent customModelData(Integer data) {
        if (meta != null && data != null) meta.setCustomModelData(data);
        return this;
    }

    public ItemComponent damage(Integer dmg) {
        if (meta instanceof Damageable d && dmg != null) d.setDamage(dmg);
        return this;
    }

    public ItemComponent enchants(Map<String, Integer> enchants) {
        if (meta != null && enchants != null && !enchants.isEmpty()) {
            for (Map.Entry<String,Integer> entry : enchants.entrySet()) {
                Enchantment ench = resolveEnchant(entry.getKey());
                if (ench != null) meta.addEnchant(ench, entry.getValue(), true);
            }
        }
        return this;
    }

    private static Enchantment resolveEnchant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String keyStr = raw.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) key = NamespacedKey.minecraft(keyStr);
        return Registry.ENCHANTMENT.get(key);
    }

    public ItemComponent glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemComponent flags(ItemFlag... flags) {
        if (meta != null && flags != null && flags.length > 0) meta.addItemFlags(flags);
        return this;
    }

    public ItemComponent skullOwner(String owner) {
        if (meta instanceof SkullMeta skull && owner != null && !owner.isBlank()) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        }
        return this;
    }

    public ItemComponent hideTooltip(Boolean hide) {
        if (meta != null && hide != null) {
            try { meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}
        }
        return this;
    }

    public ItemComponent tooltipStyle(String style) {
        if (meta != null && style != null && !style.isBlank()) {
            try {
                NamespacedKey key = NamespacedKey.fromString(style);
                if (key != null) meta.setTooltipStyle(key);
            } catch (NoSuchMethodError ignored) {}
        }
        return this;
    }

    public ItemComponent itemModel(String model) {
        if (meta != null && model != null && !model.isBlank()) {
            try {
                NamespacedKey key = NamespacedKey.fromString(model);
                if (key != null) meta.setItemModel(key);
            } catch (NoSuchMethodError ignored) {}
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
