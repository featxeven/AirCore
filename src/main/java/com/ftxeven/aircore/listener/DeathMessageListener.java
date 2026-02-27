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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathMessageListener implements Listener {
    private final AirCore plugin;
    private final Map<UUID, DamageInfo> lastDamageInfo = new ConcurrentHashMap<>();

    public DeathMessageListener(AirCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Entity damager = null;
            if (event instanceof EntityDamageByEntityEvent edbe) {
                damager = edbe.getDamager();
            }
            lastDamageInfo.put(player.getUniqueId(), new DamageInfo(event.getCause(), damager));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastDamageInfo.remove(event.getPlayer().getUniqueId());
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

        DamageInfo info = lastDamageInfo.remove(player.getUniqueId());

        Player killer = player.getKiller();
        String keyStr = "death.generic";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());

        if (killer != null) {
            if (killer.equals(player)) {
                keyStr = "death.special.suicide";
            } else {
                keyStr = "death.by-player";
                placeholders.put("killer", killer.getName());
            }
        } else if (info != null) {
            EntityDamageEvent.DamageCause cause = info.cause();
            Entity damager = info.damager();

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
                case CONTACT -> keyStr = "death.environmental.contact";

                case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> {
                    if (damager instanceof LivingEntity && !(damager instanceof Player)) {
                        keyStr = "death.by-mob";
                        placeholders.put("mob", getLocalizedMobName(damager.getType()));
                    } else if (damager != null && damager.getType() == EntityType.END_CRYSTAL) {
                        keyStr = "death.explosions.crystal";
                    } else {
                        keyStr = "death.explosions.generic";
                    }
                }

                case PROJECTILE -> {
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
                            keyStr = "death.by-projectile";
                            placeholders.put("projectile", getLocalizedProjectileName(damager.getType()));
                        }
                    }
                }

                case ENTITY_ATTACK -> {
                    if (damager instanceof Player dp) {
                        keyStr = "death.by-player";
                        placeholders.put("killer", dp.getName());
                    } else if (damager != null) {
                        keyStr = "death.by-mob";
                        placeholders.put("mob", getLocalizedMobName(damager.getType()));
                    }
                }
                default -> keyStr = "death.generic";
            }
        }

        final String finalRaw = plugin.lang().get(keyStr);
        event.deathMessage(null);

        plugin.scheduler().runTask(() -> {
            Component component = MessageUtil.mini(player, finalRaw, placeholders);
            Bukkit.broadcast(component);
        });
    }

    private String getLocalizedMobName(EntityType type) {
        String path = "death.entity-names.mobs." + type.name();
        return plugin.lang().get(path);
    }

    private String getLocalizedProjectileName(EntityType type) {
        String path = "death.entity-names.projectiles." + type.name();
        return plugin.lang().get(path);
    }

    private record DamageInfo(EntityDamageEvent.DamageCause cause, Entity damager) {}
}