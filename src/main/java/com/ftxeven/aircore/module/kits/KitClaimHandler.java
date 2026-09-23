package com.ftxeven.aircore.module.kits;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.database.cache.CooldownCache;
import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.ItemDelivery;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

public final class KitClaimHandler {

    public sealed interface Verdict {
        record Claimed() implements Verdict {}
        record Dropped() implements Verdict {}
        record InventoryFull(int requiredSlots) implements Verdict {}
        record OnCooldown(Instant expiresAt) implements Verdict {}
        record AlreadyClaimed() implements Verdict {}
        record BlockedWorld() implements Verdict {}
    }

    private static final Pattern CLAIM_PLACEHOLDERS = Pattern.compile("%(owner(?:_realname)?|date|time)%");

    private final ConfigManager configs;
    private final CooldownCache cooldowns;
    private final PlayerService players;

    public KitClaimHandler(ConfigManager configs, CooldownCache cooldowns, PlayerService players) {
        this.configs = configs;
        this.cooldowns = cooldowns;
        this.players = players;
    }

    public Verdict claim(Player recipient, Kit kit) {
        Optional<Verdict> blocked = checkEligibility(recipient, kit);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        Verdict delivered = deliver(recipient, kit);
        if (delivered instanceof Verdict.Claimed || delivered instanceof Verdict.Dropped) {
            recordClaim(recipient, kit, Instant.now());
        }
        return delivered;
    }

    public Verdict give(Player recipient, Kit kit) {
        if (isBlockedInWorld(recipient)) {
            return new Verdict.BlockedWorld();
        }
        return deliver(recipient, kit);
    }

    public Optional<Verdict> preview(Player player, Kit kit) {
        return checkEligibility(player, kit);
    }

    public boolean isAvailable(Player player, Kit kit) {
        return preview(player, kit).isEmpty();
    }

    public boolean hasInventorySpace(Player player, Kit kit) {
        return checkInventorySpace(player, kit).isEmpty();
    }

    public boolean isOnCooldown(Player player, Kit kit) {
        return !kit.oneTime()
                && effectiveCooldownSeconds(player, kit) > 0
                && activeCooldown(player, kit, Instant.now()).isPresent();
    }

    public int configuredCooldownSeconds(Kit kit) {
        return kit.cooldownSeconds() != null ? kit.cooldownSeconds() : configs.kits().defaults().cooldown();
    }

    // Eligibility

    private Optional<Verdict> checkEligibility(Player player, Kit kit) {
        if (isBlockedInWorld(player)) {
            return Optional.of(new Verdict.BlockedWorld());
        }

        Instant now = Instant.now();
        if (kit.oneTime()) {
            boolean alreadyClaimed = !player.hasPermission(Permissions.Bypass.ONETIME)
                    && activeCooldown(player, kit, now).isPresent();
            if (alreadyClaimed) {
                return Optional.of(new Verdict.AlreadyClaimed());
            }
        } else if (effectiveCooldownSeconds(player, kit) > 0) {
            Optional<Cooldown> active = activeCooldown(player, kit, now);
            if (active.isPresent()) {
                return Optional.of(new Verdict.OnCooldown(active.get().expiresAt()));
            }
        }

        return checkInventorySpace(player, kit);
    }

    private Optional<Verdict> checkInventorySpace(Player player, Kit kit) {
        if (kit.dropOnFullInventory()) {
            return Optional.empty();
        }
        int shortage = kit.exactSlots()
                ? planExact(player, kit.items()).shortageSlots()
                : ItemDelivery.shortageSlots(player.getInventory().getStorageContents(), kit.nonEmptyItems());
        return shortage > 0 ? Optional.of(new Verdict.InventoryFull(shortage)) : Optional.empty();
    }

    // Delivery

    private Verdict deliver(Player recipient, Kit kit) {
        return kit.exactSlots() ? deliverExact(recipient, kit) : deliverBulk(recipient, kit);
    }

    private Verdict deliverBulk(Player recipient, Kit kit) {
        ItemStack[] items = resolveClaimPlaceholders(recipient, kit.nonEmptyItems());
        ItemDelivery.BulkResult result = ItemDelivery.giveAll(recipient, items, kit.dropOnFullInventory());
        return switch (result.result()) {
            case REJECTED -> new Verdict.InventoryFull(result.requiredSlots());
            case DROPPED -> new Verdict.Dropped();
            case DELIVERED -> new Verdict.Claimed();
        };
    }

    private Verdict deliverExact(Player recipient, Kit kit) {
        ExactPlan plan = planExact(recipient, resolveClaimPlaceholders(recipient, kit.items()));
        if (plan.shortageSlots() > 0 && !kit.dropOnFullInventory()) {
            return new Verdict.InventoryFull(plan.shortageSlots());
        }

        PlayerInventory inventory = recipient.getInventory();
        plan.exactMain().forEach((slot, item) -> inventory.setItem(slot, item.clone()));
        if (plan.offhand() != null) inventory.setItemInOffHand(plan.offhand().clone());
        if (plan.boots() != null) inventory.setBoots(plan.boots().clone());
        if (plan.leggings() != null) inventory.setLeggings(plan.leggings().clone());
        if (plan.chestplate() != null) inventory.setChestplate(plan.chestplate().clone());
        if (plan.helmet() != null) inventory.setHelmet(plan.helmet().clone());

        if (plan.leftoverItems().length == 0) {
            return new Verdict.Claimed();
        }
        ItemDelivery.BulkResult bulk = ItemDelivery.giveAll(recipient, plan.leftoverItems(), true);
        return bulk.result() == ItemDelivery.Result.DROPPED ? new Verdict.Dropped() : new Verdict.Claimed();
    }

    private ExactPlan planExact(Player recipient, ItemStack[] items) {
        PlayerInventory inventory = recipient.getInventory();

        ItemStack[] mainSnapshot = inventory.getStorageContents().clone();
        Map<Integer, ItemStack> exactMain = new LinkedHashMap<>();
        List<ItemStack> leftover = new ArrayList<>();

        boolean offhandFree = isEmpty(inventory.getItemInOffHand());
        boolean bootsFree = isEmpty(inventory.getBoots());
        boolean leggingsFree = isEmpty(inventory.getLeggings());
        boolean chestplateFree = isEmpty(inventory.getChestplate());
        boolean helmetFree = isEmpty(inventory.getHelmet());

        ItemStack offhand = null, boots = null, leggings = null, chestplate = null, helmet = null;

        for (int slot = 0; slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (item == null) {
                continue;
            }
            if (slot < Kit.MAIN_SIZE) {
                if (isEmpty(mainSnapshot[slot])) {
                    mainSnapshot[slot] = item;
                    exactMain.put(slot, item);
                } else {
                    leftover.add(item);
                }
            } else if (slot == Kit.OFFHAND_SLOT) {
                if (offhandFree) offhand = item; else leftover.add(item);
            } else if (slot == Kit.BOOTS_SLOT) {
                if (bootsFree) boots = item; else leftover.add(item);
            } else if (slot == Kit.LEGGINGS_SLOT) {
                if (leggingsFree) leggings = item; else leftover.add(item);
            } else if (slot == Kit.CHESTPLATE_SLOT) {
                if (chestplateFree) chestplate = item; else leftover.add(item);
            } else if (slot == Kit.HELMET_SLOT) {
                if (helmetFree) helmet = item; else leftover.add(item);
            }
        }

        ItemStack[] leftoverItems = leftover.toArray(new ItemStack[0]);
        int shortage = leftoverItems.length == 0 ? 0 : ItemDelivery.shortageSlots(mainSnapshot, leftoverItems);

        return new ExactPlan(exactMain, offhand, boots, leggings, chestplate, helmet, leftoverItems, shortage);
    }

    private record ExactPlan(
            Map<Integer, ItemStack> exactMain,
            @Nullable ItemStack offhand,
            @Nullable ItemStack boots,
            @Nullable ItemStack leggings,
            @Nullable ItemStack chestplate,
            @Nullable ItemStack helmet,
            ItemStack[] leftoverItems,
            int shortageSlots
    ) {}

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    // Claim placeholders

    private ItemStack[] resolveClaimPlaceholders(Player recipient, ItemStack[] items) {
        ItemStack[] resolved = null;
        Map<String, String> values = null;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || !hasClaimPlaceholder(item)) {
                continue;
            }
            if (values == null) {
                values = claimPlaceholderValues(recipient);
            }
            if (resolved == null) {
                resolved = items.clone();
            }
            resolved[i] = applyClaimPlaceholders(item, values);
        }

        return resolved != null ? resolved : items;
    }

    private boolean hasClaimPlaceholder(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName() && CLAIM_PLACEHOLDERS.matcher(MiniText.plain(meta.displayName())).find()) {
            return true;
        }
        if (meta.hasLore()) {
            for (Component line : meta.lore()) {
                if (CLAIM_PLACEHOLDERS.matcher(MiniText.plain(line)).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, String> claimPlaceholderValues(Player recipient) {
        Map<String, String> values = new HashMap<>(4);
        players.formatDisplayName(values, "owner", recipient);
        MainConfig.Formatting formatting = configs.main().formatting();
        Instant now = Instant.now();
        values.put("date", TimeFormatter.date(now, formatting));
        values.put("time", TimeFormatter.time(now, formatting));
        return values;
    }

    private ItemStack applyClaimPlaceholders(ItemStack item, Map<String, String> values) {
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        TextReplacementConfig replacement = TextReplacementConfig.builder()
                .match(CLAIM_PLACEHOLDERS)
                .replacement((match, builder) -> MiniText.parseDynamic(values.get(match.group(1))))
                .build();

        if (meta.hasDisplayName()) {
            meta.displayName(meta.displayName().replaceText(replacement));
        }
        if (meta.hasLore()) {
            meta.lore(meta.lore().stream().map(line -> line.replaceText(replacement)).toList());
        }
        clone.setItemMeta(meta);
        return clone;
    }

    private void recordClaim(Player recipient, Kit kit, Instant now) {
        if (kit.oneTime()) {
            cooldowns.start(CooldownScope.KIT, recipient.getUniqueId(), kit.name(), "", CooldownRepository.PERMANENT);
            return;
        }
        int effectiveSeconds = effectiveCooldownSeconds(recipient, kit);
        if (effectiveSeconds > 0) {
            cooldowns.start(CooldownScope.KIT, recipient.getUniqueId(), kit.name(), "", now.plusSeconds(effectiveSeconds));
        }
    }

    private Optional<Cooldown> activeCooldown(Player recipient, Kit kit, Instant now) {
        return cooldowns.peek(CooldownScope.KIT, recipient.getUniqueId(), kit.name(), "", now);
    }

    private int effectiveCooldownSeconds(Player player, Kit kit) {
        String bypass = Permissions.Bypass.kitCooldown(kit.name());
        if (player.hasPermission(bypass) || player.hasPermission(Permissions.Bypass.kitCooldown("*"))) {
            return -1;
        }
        int configured = configuredCooldownSeconds(kit);
        OptionalDouble tier = PermissionTiers.resolve(player, bypass, PermissionTiers.Pick.LOWEST);
        return tier.isPresent() ? Math.clamp((int) tier.getAsDouble(), 0, configured) : configured;
    }

    private boolean isBlockedInWorld(Player player) {
        if (player.hasPermission(Permissions.Bypass.KIT_DISABLED_WORLDS)) {
            return false;
        }
        return configs.kits().general().disabledWorlds().contains(player.getWorld().getName());
    }
}