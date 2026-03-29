package com.ftxeven.aircore.core.module.kit;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class KitService {

    private final AirCore plugin;
    private final File file;
    private final YamlConfiguration config;
    private final Map<String, List<ItemStack>> kitCache = new HashMap<>();

    public KitService(AirCore plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataFolder.getAbsolutePath());
        }
        this.file = new File(dataFolder, "kits.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create kits.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        preloadKits();
    }

    private void preloadKits() {
        kitCache.clear();
        var section = config.getConfigurationSection("kits");
        if (section == null) return;
        for (String kitName : section.getKeys(false)) {
            List<?> serialized = config.getList("kits." + kitName + ".items");
            if (serialized == null) continue;
            List<ItemStack> items = new ArrayList<>();
            for (Object obj : serialized) {
                if (obj instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    ItemStack item = ItemStack.deserialize((Map<String, Object>) m);
                    if (!item.getType().isAir() && item.getAmount() > 0) {
                        items.add(item);
                    }
                }
            }
            kitCache.put(kitName.toLowerCase(), items);
        }
    }

    public List<ItemStack> getKitItems(String kitName) {
        return kitCache.getOrDefault(kitName.toLowerCase(), List.of());
    }

    public boolean createKit(Player player, String kitName, boolean oneTime, boolean autoEquip, long cooldownSeconds) {
        if (config.contains("kits." + kitName)) return false;
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            items.add(stack.serialize());
        }
        config.set("kits." + kitName + ".cooldown", cooldownSeconds);
        config.set("kits." + kitName + ".one-time", oneTime);
        config.set("kits." + kitName + ".auto-equip", autoEquip);
        config.set("kits." + kitName + ".items", items);
        try {
            config.save(file);
            preloadKits();
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save kits.yml: " + e.getMessage());
            return false;
        }
    }

    public YamlConfiguration getConfig() { return config; }

    public void saveConfig() {
        try {
            config.save(file);
            preloadKits();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save kits.yml: " + e.getMessage());
        }
    }

    public void grantFirstJoinKit(Player player) {
        String kitName = plugin.config().kitsFirstJoinKit();
        if (kitName.isEmpty()) return;
        UUID uuid = player.getUniqueId();
        plugin.scheduler().runAsync(() -> {
            var data = plugin.database().kits().load(uuid, kitName);
            if (data.lastClaim() != 0 || data.oneTimeClaimed()) return;
            List<ItemStack> kitItems = getKitItems(kitName);
            if (kitItems.isEmpty()) return;
            long now = System.currentTimeMillis() / 1000;
            long cooldown = config.getLong("kits." + kitName + ".cooldown", 0);
            boolean autoEquip = config.getBoolean("kits." + kitName + ".auto-equip", false);
            plugin.scheduler().runEntityTask(player, () -> {
                for (ItemStack item : kitItems) {
                    ItemStack toAdd = item.clone();
                    boolean equipped = false;
                    if (autoEquip) equipped = tryEquip(player, resolveEquipmentSlot(toAdd), toAdd);
                    if (!equipped) player.getInventory().addItem(toAdd);
                }
            });
            plugin.scheduler().runAsync(() -> plugin.database().kits().save(uuid, kitName, now, true, cooldown));
        });
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

    public boolean isOnCooldown(UUID uuid, String kitName) {
        var data = plugin.database().kits().load(uuid, kitName);
        long now = System.currentTimeMillis() / 1000;
        long elapsed = now - data.lastClaim();
        long cooldown = config.getLong("kits." + kitName + ".cooldown", 0);
        return cooldown > 0 && elapsed < cooldown;
    }

    public boolean isAvailable(UUID uuid, String kitName) {
        var data = plugin.database().kits().load(uuid, kitName);
        if (config.getBoolean("kits." + kitName + ".one-time", false) && data.oneTimeClaimed()) return false;
        return !isOnCooldown(uuid, kitName);
    }

    public boolean hasPermission(UUID uuid, String kitName) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.hasPermission("aircore.command.kit." + kitName.toLowerCase());
    }

    public long getCooldownSeconds(UUID uuid, String kitName) {
        var data = plugin.database().kits().load(uuid, kitName);
        long now = System.currentTimeMillis() / 1000;
        long elapsed = now - data.lastClaim();
        long cooldown = config.getLong("kits." + kitName + ".cooldown", 0);
        return Math.max(cooldown - elapsed, 0);
    }

    public boolean exists(String kitName) { return kitName != null && kitCache.containsKey(kitName.toLowerCase()); }

    public boolean tryEquipExternal(Player player, ItemStack item) {
        return tryEquip(player, resolveEquipmentSlot(item), item);
    }

    public EquipmentSlot resolveEquipmentSlotExternal(ItemStack item) {
        return resolveEquipmentSlot(item);
    }

    public boolean isSlotEmptyExternal(Player player, EquipmentSlot slot) {
        PlayerInventory inv = player.getInventory();
        return switch (slot) {
            case HEAD -> isEmpty(inv.getHelmet());
            case CHEST -> isEmpty(inv.getChestplate());
            case LEGS -> isEmpty(inv.getLeggings());
            case FEET -> isEmpty(inv.getBoots());
            case OFF_HAND -> isEmpty(inv.getItemInOffHand());
            default -> false;
        };
    }
}