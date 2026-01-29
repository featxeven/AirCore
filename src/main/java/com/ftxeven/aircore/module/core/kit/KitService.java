package com.ftxeven.aircore.module.core.kit;

import com.ftxeven.aircore.AirCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        this.config = YamlConfiguration.loadConfiguration(file);

        preloadKits();
    }

    private void preloadKits() {
        kitCache.clear();
        var section = config.getConfigurationSection("kits");
        if (section == null) return;

        for (String kitName : section.getKeys(false)) {
            List<Map<?, ?>> serialized = config.getMapList("kits." + kitName + ".items");
            List<ItemStack> items = new ArrayList<>();
            for (Map<?, ?> m : serialized) {
                @SuppressWarnings("unchecked")
                ItemStack item = ItemStack.deserialize((Map<String, Object>) m);
                if (!item.getType().isAir() && item.getAmount() > 0) {
                    items.add(item);
                }
            }
            kitCache.put(kitName.toLowerCase(), items);
        }
    }

    public List<ItemStack> getKitItems(String kitName) {
        return kitCache.getOrDefault(kitName.toLowerCase(), List.of());
    }

    public boolean createKit(Player player, String kitName, boolean oneTime, long cooldownSeconds) {
        if (config.contains("kits." + kitName)) {
            return false;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            items.add(stack.serialize());
        }

        config.set("kits." + kitName + ".cooldown", cooldownSeconds);
        config.set("kits." + kitName + ".one-time", oneTime);
        config.set("kits." + kitName + ".items", items);

        try {
            config.save(file);
            // refresh cache
            preloadKits();
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save kits.yml: " + e.getMessage());
            return false;
        }
    }

    public YamlConfiguration getConfig() {
        return config;
    }

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

            plugin.scheduler().runEntityTask(player, () -> {
                player.getInventory().addItem(
                        kitItems.stream().map(ItemStack::clone).toArray(ItemStack[]::new)
                );
            });

            plugin.scheduler().runAsync(() ->
                    plugin.database().kits().save(uuid, kitName, now, true, cooldown)
            );
        });
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
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        if (data.oneTimeClaimed() && !player.hasPermission("aircore.bypass.kit.onetime")) {
            return false;
        }

        return !isOnCooldown(uuid, kitName) || player.hasPermission("aircore.bypass.kit.cooldown");
    }

    public boolean hasPermission(UUID uuid, String kitName) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return player.hasPermission("aircore.command.kit." + kitName.toLowerCase());
    }

    public long getCooldownSeconds(UUID uuid, String kitName) {
        var data = plugin.database().kits().load(uuid, kitName);
        long now = System.currentTimeMillis() / 1000;
        long elapsed = now - data.lastClaim();
        long cooldown = config.getLong("kits." + kitName + ".cooldown", 0);
        long remaining = cooldown - elapsed;
        return Math.max(remaining, 0);
    }

    public boolean exists(String kitName) {
        if (kitName == null || kitName.isBlank()) return false;
        return kitCache.containsKey(kitName.toLowerCase());
    }
}