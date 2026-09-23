package com.ftxeven.aircore.module.extras;

import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.ItemDisplay;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;

final class DeathMessageResolver {

    record Result(String langKey, Map<String, String> placeholders) {
        private static Result of(String langKey) {
            return new Result(langKey, Map.of());
        }
    }

    private final LangConfig lang;
    private final PlayerService players;

    DeathMessageResolver(LangConfig lang, PlayerService players) {
        this.lang = lang;
        this.players = players;
    }

    Result resolve(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause == null) {
            return Result.of("death.generic");
        }

        return switch (cause.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> byEntity(cause);
            case PROJECTILE -> byProjectile(cause);
            case FALLING_BLOCK -> byFallingBlock(cause);
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> byExplosion(cause);
            case SUICIDE -> Result.of("death.special.suicide");
            case FALL -> Result.of("death.environmental.fall");
            case VOID -> Result.of("death.environmental.void");
            case LAVA -> Result.of("death.environmental.lava");
            case FIRE, FIRE_TICK -> Result.of("death.environmental.fire");
            case DROWNING -> Result.of("death.environmental.drowning");
            case LIGHTNING -> Result.of("death.environmental.lightning");
            case SUFFOCATION -> Result.of("death.environmental.suffocation");
            case STARVATION -> Result.of("death.environmental.starvation");
            case POISON -> Result.of("death.environmental.poison");
            case MAGIC -> Result.of("death.environmental.magic");
            case WITHER -> Result.of("death.environmental.withering");
            case CONTACT -> Result.of("death.environmental.contact");
            case THORNS -> byThorns(cause);
            case DRAGON_BREATH -> Result.of("death.environmental.dragon-breath");
            case FREEZE -> Result.of("death.environmental.freeze");
            case HOT_FLOOR -> Result.of("death.environmental.hot-floor");
            case DRYOUT -> Result.of("death.environmental.dryout");
            case SONIC_BOOM -> Result.of("death.environmental.sonic-boom");
            case FLY_INTO_WALL -> Result.of("death.environmental.fly-into-wall");
            case CRAMMING -> Result.of("death.environmental.cramming");
            case WORLD_BORDER -> Result.of("death.environmental.world-border");
            default -> Result.of("death.generic");
        };
    }

    private Result byEntity(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return Result.of("death.generic");
        }
        Entity damager = byEntity.getDamager();
        return damager instanceof Player killer ? byPlayer(killer, weaponOf(killer)) : byMob(damager);
    }

    private Result byProjectile(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent byEntity) || !(byEntity.getDamager() instanceof Projectile projectile)) {
            return Result.of("death.generic");
        }
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player killer) {
            return byPlayer(killer, weaponOf(killer));
        }
        if (shooter instanceof LivingEntity mob) {
            return byMob(mob);
        }
        String key = "death.entities.projectiles." + projectile.getType().getKey().getKey();
        return new Result("death.by-projectile", Map.of("projectile", lang.get(key).getFirst()));
    }

    private Result byFallingBlock(EntityDamageEvent cause) {
        if (cause instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof FallingBlock block
                && block.getBlockData().getMaterial() == Material.POINTED_DRIPSTONE) {
            return Result.of("death.environmental.stalactite");
        }
        return Result.of("death.environmental.falling-block");
    }

    private Result byExplosion(EntityDamageEvent cause) {
        boolean crystal = cause instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof EnderCrystal;
        return Result.of(crystal ? "death.explosions.crystal" : "death.explosions.generic");
    }

    private Result byThorns(EntityDamageEvent cause) {
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            Map<String, String> placeholders = new HashMap<>();
            putEntityName(placeholders, "target", byEntity.getDamager());
            return new Result("death.environmental.thorns", placeholders);
        }
        return Result.of("death.generic");
    }

    private Result byPlayer(Player killer, ItemStack weapon) {
        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, "killer", killer);
        if (weapon != null) {
            ItemDisplay.formatInto(placeholders, "item", weapon, lang);
        } else {
            String unarmed = lang.get("death.entities.unarmed").getFirst();
            placeholders.put("item", unarmed);
            placeholders.put("item_no_tooltip", unarmed);
        }
        return new Result("death.by-player", placeholders);
    }

    private Result byMob(Entity damager) {
        return new Result("death.by-mob", Map.of("mob", mobName(damager)));
    }

    private ItemStack weaponOf(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!main.getType().isAir()) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off.getType().isAir() ? null : off;
    }

    private void putEntityName(Map<String, String> placeholders, String key, Entity entity) {
        if (entity instanceof Player player) {
            players.formatDisplayName(placeholders, key, player);
        } else {
            placeholders.put(key, mobName(entity));
        }
    }

    private String mobName(Entity entity) {
        return lang.get("death.entities.mobs." + entity.getType().getKey().getKey()).getFirst();
    }
}