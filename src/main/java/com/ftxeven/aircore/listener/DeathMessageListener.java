package com.ftxeven.aircore.listener;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathMessageListener implements Listener {
    private final AirCore plugin;
    private final Map<UUID, EntityDamageEvent.DamageCause> lastCauses = new ConcurrentHashMap<>();

    public DeathMessageListener(AirCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastCauses.put(player.getUniqueId(), event.getCause());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.config().deathMessagesEnabled()) return;

        Player player = event.getEntity();
        String worldName = player.getWorld().getName();

        if (plugin.config().deathMessagesDisabledWorlds().contains(worldName)) {
            return;
        }

        var key = new NamespacedKey(plugin, "death_reason");
        if (player.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            player.getPersistentDataContainer().remove(key);
            event.deathMessage(null);
            return;
        }

        Player killer = player.getKiller();
        String keyStr = "death.generic";
        Map<String,String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());

        EntityDamageEvent.DamageCause cause = lastCauses.remove(player.getUniqueId());

        if (killer != null) {
            if (killer.equals(player)) {
                keyStr = "death.special.suicide";
            } else {
                keyStr = "death.by-player";
                placeholders.put("killer", killer.getName());
            }
        } else if (cause != null) {
            switch (cause) {
                case FALL -> keyStr = "death.environmental.fall";
                case FIRE, FIRE_TICK -> keyStr = "death.environmental.fire";
                case LAVA -> keyStr = "death.environmental.lava";
                case DROWNING -> keyStr = "death.environmental.drowning";
                case VOID -> keyStr = "death.environmental.void";
                case LIGHTNING -> keyStr = "death.environmental.lightning";
                case STARVATION -> keyStr = "death.environmental.starvation";
                case SUFFOCATION -> keyStr = "death.environmental.suffocation";
                case POISON -> keyStr = "death.environmental.poison";
                case WITHER -> keyStr = "death.environmental.withering";
                case MAGIC -> keyStr = "death.environmental.magic";

                case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> {
                    EntityDamageEvent damage = player.getLastDamageCause();
                    if (damage instanceof EntityDamageByEntityEvent entityEvent) {
                        Entity damager = entityEvent.getDamager();
                        if (damager.getType() == EntityType.END_CRYSTAL) {
                            keyStr = "death.explosions.crystal";
                        } else {
                            keyStr = "death.explosions.generic";
                        }
                    } else keyStr = "death.explosions.generic";
                }

                case PROJECTILE -> {
                    EntityDamageEvent damage = player.getLastDamageCause();
                    if (damage instanceof EntityDamageByEntityEvent entityEvent) {
                        Entity damager = entityEvent.getDamager();
                        if (damager instanceof Projectile projectile) {
                            ProjectileSource shooter = projectile.getShooter();
                            if (shooter instanceof Player sp) {
                                if (sp.equals(player)) keyStr = "death.special.suicide";
                                else {
                                    keyStr = "death.by-player";
                                    placeholders.put("killer", sp.getName());
                                }
                            } else if (shooter instanceof Entity mob) {
                                keyStr = "death.by-mob";
                                placeholders.put("mob", getLocalizedMobName(mob.getType()));
                            } else {
                                keyStr = "death.by-mob";
                                placeholders.put("mob", getLocalizedMobName(damager.getType()));
                            }
                        }
                    }
                }

                case ENTITY_ATTACK -> {
                    EntityDamageEvent damage = player.getLastDamageCause();
                    if (damage instanceof EntityDamageByEntityEvent entityEvent) {
                        Entity damager = entityEvent.getDamager();
                        if (damager instanceof Player dp) {
                            keyStr = "death.by-player";
                            placeholders.put("killer", dp.getName());
                        } else {
                            keyStr = "death.by-mob";
                            placeholders.put("mob", getLocalizedMobName(damager.getType()));
                        }
                    } else keyStr = "death.by-mob";
                }

                default -> keyStr = "death.generic";
            }
        }

        String raw = plugin.lang().get(keyStr);
        if (raw.isBlank()) raw = keyStr;
        Component component = MessageUtil.mini(player, raw, placeholders);

        event.deathMessage(null);
        Bukkit.broadcast(component);
    }

    private String getLocalizedMobName(EntityType type) {
        // Try hostile mobs first
        String hostilePath = "death.mob-names.hostile." + type.name();
        String localized = plugin.lang().get(hostilePath);
        if (!localized.isBlank()) return localized;

        // Then try projectiles
        String projectilePath = "death.mob-names.projectiles." + type.name();
        localized = plugin.lang().get(projectilePath);
        if (!localized.isBlank()) return localized;

        // Fallback to raw enum name if nothing found
        return type.name();
    }
}
