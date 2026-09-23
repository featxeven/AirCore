package com.ftxeven.aircore.module.kits;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.Eligibility;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KitsModule {

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final CacheManager cache;
    private final KitClaimHandler claims;

    private final AtomicBoolean warnedMissingFirstJoinKit = new AtomicBoolean(false);

    public KitsModule(JavaPlugin plugin, ConfigManager configs, CacheManager cache, ServiceManager services) {
        this.plugin = plugin;
        this.configs = configs;
        this.cache = cache;
        this.claims = new KitClaimHandler(configs, cache.cooldowns(), services.players());
    }

    // Lookup / listing

    public Optional<Kit> find(String name) {
        return cache.kits().find(name);
    }

    public List<Kit> findAll() {
        return cache.kits().findAll();
    }

    public List<String> accessibleKitNames(Permissible sender) {
        List<String> names = new ArrayList<>();
        for (Kit kit : cache.kits().findAll()) {
            if (canAccess(sender, kit)) {
                names.add(kit.name());
            }
        }
        return names;
    }

    public List<String> allKitNames() {
        return findAll().stream().map(Kit::name).toList();
    }

    public Optional<Kit> firstJoinKit() {
        String name = configs.kits().general().firstJoin();
        if (name.isBlank()) {
            return Optional.empty();
        }
        Optional<Kit> kit = find(name);
        if (kit.isEmpty() && warnedMissingFirstJoinKit.compareAndSet(false, true)) {
            plugin.getLogger().warning("general.first-join in modules/kits.yml points at '" + name
                    + "', which isn't a registered kit - no kit will be granted on join until this is fixed");
        }
        return kit;
    }

    public boolean isAvailable(Player player, Kit kit) {
        return claims.isAvailable(player, kit);
    }

    public boolean canAccess(Permissible sender, Kit kit) {
        return !kit.requiresPermission() || Permissions.Access.hasKit(sender, kit.name());
    }

    public boolean canClaim(Player player, Kit kit) {
        return canAccess(player, kit) && isAvailable(player, kit);
    }

    public boolean hasInventorySpace(Player player, Kit kit) {
        return claims.hasInventorySpace(player, kit);
    }

    public boolean isOnCooldown(Player player, Kit kit) {
        return claims.isOnCooldown(player, kit);
    }

    public Optional<KitClaimHandler.Verdict> preview(Player player, Kit kit) {
        return claims.preview(player, kit);
    }

    public sealed interface SelfClaimAttempt {
        record Eligible() implements SelfClaimAttempt {}
        record Denied(Eligibility.Denied denial) implements SelfClaimAttempt {}
        record NeedsConfirmation() implements SelfClaimAttempt {}
    }

    public SelfClaimAttempt checkSelfClaim(Player player, Kit kit, boolean confirmationAvailable) {
        if (!canAccess(player, kit)) {
            return new SelfClaimAttempt.Denied(KitClaimMessages.noPermission(kit));
        }
        if (!confirmationAvailable) {
            return new SelfClaimAttempt.Eligible();
        }

        Optional<KitClaimHandler.Verdict> blocked = preview(player, kit);
        if (blocked.isPresent()) {
            return new SelfClaimAttempt.Denied((Eligibility.Denied) KitClaimMessages.self(player, kit, blocked.get(), configs));
        }
        return new SelfClaimAttempt.NeedsConfirmation();
    }

    // Building

    public static ItemStack[] captureItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] items = new ItemStack[Kit.TOTAL_SLOTS];

        ItemStack[] storage = inventory.getStorageContents();
        System.arraycopy(storage, 0, items, 0, Math.min(storage.length, Kit.MAIN_SIZE));

        items[Kit.OFFHAND_SLOT] = inventory.getItemInOffHand();
        items[Kit.BOOTS_SLOT] = inventory.getBoots();
        items[Kit.LEGGINGS_SLOT] = inventory.getLeggings();
        items[Kit.CHESTPLATE_SLOT] = inventory.getChestplate();
        items[Kit.HELMET_SLOT] = inventory.getHelmet();

        return items;
    }

    public Kit build(String name, ItemStack[] items, KitParams params, @Nullable UUID createdBy) {
        KitsConfig.Defaults defaults = configs.kits().defaults();
        boolean oneTime = params.oneTime() != null ? params.oneTime() : defaults.oneTime();
        boolean dropOnFullInventory = params.dropOnFullInventory() != null ? params.dropOnFullInventory() : defaults.dropOnFullInventory();
        boolean exactSlots = params.exactSlots() != null ? params.exactSlots() : defaults.exactSlots();
        boolean requiresPermission = params.requiresPermission() != null ? params.requiresPermission() : defaults.requiresPermission();
        return new Kit(name, items, oneTime, params.cooldownSeconds(), dropOnFullInventory, exactSlots, requiresPermission, Instant.now(), createdBy);
    }

    public Kit applyEdits(Kit existing, KitParams params, ItemStack[] items) {
        boolean oneTime = params.oneTime() != null ? params.oneTime() : existing.oneTime();
        Integer cooldownSeconds = params.cooldownSeconds() != null ? params.cooldownSeconds() : existing.cooldownSeconds();
        boolean dropOnFullInventory = params.dropOnFullInventory() != null ? params.dropOnFullInventory() : existing.dropOnFullInventory();
        boolean exactSlots = params.exactSlots() != null ? params.exactSlots() : existing.exactSlots();
        boolean requiresPermission = params.requiresPermission() != null ? params.requiresPermission() : existing.requiresPermission();
        return new Kit(existing.name(), items, oneTime, cooldownSeconds, dropOnFullInventory, exactSlots, requiresPermission, existing.createdAt(), existing.createdBy());
    }

    public void save(Kit kit) {
        cache.kits().save(kit);
    }

    public boolean delete(String name) {
        Optional<Kit> kit = find(name);
        if (kit.isEmpty() || !cache.kits().delete(name)) {
            return false;
        }
        cache.cooldowns().clearAllForKey(CooldownScope.KIT, kit.get().name());
        return true;
    }

    // Claiming

    public KitClaimHandler.Verdict claim(Player recipient, Kit kit) {
        return claims.claim(recipient, kit);
    }

    public KitClaimHandler.Verdict give(Player recipient, Kit kit) {
        return claims.give(recipient, kit);
    }

    public int cooldownSeconds(Kit kit) {
        return claims.configuredCooldownSeconds(kit);
    }

    // Lifecycle

    public void handleJoin(Player player, boolean firstJoin) {
        if (!firstJoin) {
            return;
        }
        firstJoinKit().ifPresent(kit -> claims.give(player, kit));
    }

    public void handleQuit(UUID uuid) {
        resetClaims(uuid, configs.kits().cooldowns().resetOnLogout(), configs.kits().oneTime().resetOnLogout());
    }

    public void handleDeath(UUID uuid) {
        resetClaims(uuid, configs.kits().cooldowns().resetOnDeath(), configs.kits().oneTime().resetOnDeath());
    }

    private void resetClaims(UUID uuid, boolean resetCooldownKits, boolean resetOneTimeKits) {
        if (!resetCooldownKits && !resetOneTimeKits) {
            return;
        }
        for (Kit kit : cache.kits().findAll()) {
            boolean applies = kit.oneTime() ? resetOneTimeKits : resetCooldownKits;
            if (applies) {
                cache.cooldowns().clear(CooldownScope.KIT, uuid, kit.name(), "");
            }
        }
    }
}