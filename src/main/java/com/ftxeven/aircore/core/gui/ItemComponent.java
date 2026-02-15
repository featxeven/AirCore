package com.ftxeven.aircore.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import java.util.*;

public final class ItemComponent {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemComponent(Material material) {
        this.item = new ItemStack(material == null ? Material.AIR : material);
        this.meta = item.getItemMeta();
    }

    public ItemComponent amount(int amount) {
        if (meta != null) item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return this;
    }

    public ItemComponent name(Component name) { if (meta != null) meta.displayName(name); return this; }

    public ItemComponent lore(List<Component> lore) { if (meta != null && lore != null) meta.lore(lore); return this; }

    public ItemComponent customModelData(Integer data) { if (meta != null) meta.setCustomModelData(data); return this; }

    public ItemComponent damage(Integer dmg) { if (meta instanceof Damageable d && dmg != null) d.setDamage(dmg); return this; }

    public ItemComponent enchants(Map<String, Integer> enchants) {
        if (meta != null && enchants != null) {
            enchants.forEach((k, v) -> {
                NamespacedKey key = NamespacedKey.fromString(k.toLowerCase(Locale.ROOT));
                Enchantment ench = Registry.ENCHANTMENT.get(key != null ? key : NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
                if (ench != null) meta.addEnchant(ench, v, true);
            });
        }
        return this;
    }

    public ItemComponent glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemComponent flags(ItemFlag... flags) { if (meta != null && flags.length > 0) meta.addItemFlags(flags); return this; }

    public ItemComponent skullOwner(String owner) {
        if (meta instanceof SkullMeta s && owner != null && !owner.isBlank()) s.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        return this;
    }

    public ItemComponent hideTooltip(Boolean hide) {
        try { if (meta != null && hide != null) meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}
        return this;
    }

    public ItemComponent tooltipStyle(String style) { return applyKey(style, ItemMeta::setTooltipStyle); }

    public ItemComponent itemModel(String model) { return applyKey(model, ItemMeta::setItemModel); }

    private ItemComponent applyKey(String s, java.util.function.BiConsumer<ItemMeta, NamespacedKey> consumer) {
        if (meta != null && s != null && !s.isBlank()) {
            try { NamespacedKey key = NamespacedKey.fromString(s); if (key != null) consumer.accept(meta, key); } catch (NoSuchMethodError ignored) {}
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}