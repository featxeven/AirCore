package com.ftxeven.aircore.module.economy.worth;

import com.ftxeven.aircore.core.hook.HookRegistry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

public final class WorthCalculator {

    // vanilla can't nest a shulker inside a bundle or another shulker, but this stays shallow on
    // purpose in case a future item type allows it anyway
    private static final int MAX_CONTAINER_DEPTH = 2;

    private final Supplier<WorthItemsConfig> items;
    private final Supplier<WorthModifiersConfig> modifiers;
    private final HookRegistry hooks;
    private final ItemMatcher matcher;

    public WorthCalculator(Supplier<WorthItemsConfig> items, Supplier<WorthModifiersConfig> modifiers, HookRegistry hooks) {
        this.items = items;
        this.modifiers = modifiers;
        this.hooks = hooks;
        this.matcher = new ItemMatcher(hooks);
    }

    public record Appraisal(boolean sellable, double total, Set<String> invalidKeys) {
        private static Appraisal merge(double total, Set<String> invalidKeys) {
            return new Appraisal(invalidKeys.isEmpty(), total, Set.copyOf(invalidKeys));
        }
    }

    public Appraisal appraise(ItemStack stack) {
        return appraise(stack, 0);
    }

    private Appraisal appraise(ItemStack stack, int depth) {
        if (stack == null || stack.getType().isAir()) {
            return new Appraisal(true, 0, Set.of());
        }

        Set<String> invalid = new LinkedHashSet<>();
        double total = 0;

        OptionalDouble base = baseWorth(stack);
        if (base.isPresent()) {
            total += applyModifiers(stack, base.getAsDouble()) * stack.getAmount();
        } else {
            invalid.add(keyOf(stack));
        }

        if (depth < MAX_CONTAINER_DEPTH) {
            for (ItemStack content : containerContentsOf(stack)) {
                Appraisal inner = appraise(content, depth + 1);
                total += inner.total();
                invalid.addAll(inner.invalidKeys());
            }
        }

        return Appraisal.merge(total, invalid);
    }

    private OptionalDouble baseWorth(ItemStack stack) {
        for (WorthRule rule : items.get().customRules()) {
            if (matcher.matches(stack, rule.match())) {
                return OptionalDouble.of(rule.worth());
            }
        }
        return items.get().findMaterial(keyOf(stack));
    }

    private String keyOf(ItemStack stack) {
        String pluginId = hooks.identify(stack);
        return pluginId != null ? pluginId : stack.getType().getKey().getKey().toLowerCase(Locale.ROOT);
    }

    // Modifiers

    private double applyModifiers(ItemStack stack, double base) {
        double multiplier = 1.0 + enchantmentBonus(stack) + potionBonus(stack);
        return base * multiplier * durabilityFactor(stack);
    }

    private double enchantmentBonus(ItemStack stack) {
        double bonus = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchantmentsOf(stack).entrySet()) {
            bonus += modifiers.get().enchantmentModifier(entry.getKey().getKey().getKey()) * entry.getValue();
        }
        return bonus;
    }

    private Map<Enchantment, Integer> enchantmentsOf(ItemStack stack) {
        if (stack.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return stack.getEnchantments();
    }

    private double potionBonus(ItemStack stack) {
        WorthModifiersConfig.Potions config = modifiers.get().potions();
        if (!config.enabled() || !(stack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return 0;
        }

        double bonus = 0;

        PotionType baseType = potionMeta.getBasePotionType();
        if (baseType != null) {
            for (PotionEffect effect : baseType.getPotionEffects()) {
                bonus += effectBonus(config, effect);
            }
            bonus += tierBonus(config, baseType);
        }

        if (potionMeta.hasCustomEffects()) {
            for (PotionEffect effect : potionMeta.getCustomEffects()) {
                bonus += effectBonus(config, effect);
            }
        }

        return bonus;
    }

    private double effectBonus(WorthModifiersConfig.Potions config, PotionEffect effect) {
        String key = effect.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        return config.effects().getOrDefault(key, 0.0) * (effect.getAmplifier() + 1);
    }

    private double tierBonus(WorthModifiersConfig.Potions config, PotionType baseType) {
        String key = baseType.getKey().getKey().toLowerCase(Locale.ROOT);
        if (key.startsWith("strong_")) {
            return config.upgradedMultiplier();
        }
        if (key.startsWith("long_")) {
            return config.extendedMultiplier();
        }
        return 0;
    }

    private double durabilityFactor(ItemStack stack) {
        int max = stack.getType().getMaxDurability();
        if (max <= 0 || !(stack.getItemMeta() instanceof Damageable damageable)) {
            return 1.0;
        }

        double remainingPercent = 1.0 - ((double) damageable.getDamage() / max);
        double minPercent = modifiers.get().durability().minWorthPercent();
        return minPercent + (1.0 - minPercent) * remainingPercent;
    }

    // Containers

    private List<ItemStack> containerContentsOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            return Arrays.asList(shulkerBox.getInventory().getContents());
        }
        if (meta instanceof BundleMeta bundleMeta) {
            return bundleMeta.getItems();
        }
        return List.of();
    }
}