package com.ftxeven.aircore.core.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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

    public void lore(List<Component> lore) { if (meta != null && lore != null) meta.lore(lore); }
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

    public void skullOwner(String owner, Player viewer) {
        if (!(meta instanceof SkullMeta skullMeta) || owner == null || owner.isBlank()) return;

        if (owner.length() > 20) {
            applyCustomHead(skullMeta, owner);
            return;
        }

        if (owner.equalsIgnoreCase("%player%") || owner.equalsIgnoreCase(viewer.getName())) {
            skullMeta.setPlayerProfile(viewer.getPlayerProfile());
            return;
        }

        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
    }

    private void applyCustomHead(SkullMeta meta, String texture) {
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        String encoded;

        if (texture.length() > 100) {
            encoded = texture;
        } else {
            String url = texture.startsWith("http")
                    ? texture
                    : "http://textures.minecraft.net/texture/" + texture;

            String json = String.format("{ \"textures\": { \"SKIN\": { \"url\": \"%s\" } } }", url);
            encoded = Base64.getEncoder().encodeToString(json.getBytes());
        }

        profile.setProperty(new ProfileProperty("textures", encoded));
        meta.setPlayerProfile(profile);
    }

    public ItemComponent hideTooltip(Boolean hide) {
        try { if (meta != null && hide != null) meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}
        return this;
    }

    public ItemComponent tooltipStyle(String style) { return applyKey(style, ItemMeta::setTooltipStyle); }

    public void itemModel(String model) { applyKey(model, ItemMeta::setItemModel); }

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