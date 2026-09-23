package com.ftxeven.aircore.migration;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.migration.model.LegacyPlayer;
import com.ftxeven.aircore.migration.model.LegacyServerData;
import com.ftxeven.aircore.migration.util.LegacyText;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.PlayerProfile;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class MigrationWriter {

    private static final int NAME_MAX = 16;
    private static final int NICKNAME_MAX = 32;
    private static final int HOME_NAME_MAX = 32;
    private static final int KIT_NAME_MAX = 32;
    private static final int LOCATION_KEY_MAX = 64;
    private static final int COOLDOWN_KEY_MAX = 64;

    private final DatabaseManager database;
    private final CacheManager cache;
    private final ConfigManager configs;
    private final MigrationOptions options;
    private final MigrationReport report;

    private final Set<String> claimedNicknames = new HashSet<>();

    public MigrationWriter(DatabaseManager database, CacheManager cache, ConfigManager configs,
                           MigrationOptions options, MigrationReport report) {
        this.database = database;
        this.cache = cache;
        this.configs = configs;
        this.options = options;
        this.report = report;
    }

    // Players

    public void writePlayer(LegacyPlayer legacy) {
        UUID uuid = legacy.uuid();
        String name = legacy.name() == null ? "" : legacy.name().strip();

        if (name.isEmpty() || name.length() > NAME_MAX) {
            report.failed(MigrationReport.Category.PLAYERS, uuid + ": unusable account name '" + name + "'");
            return;
        }

        Optional<PlayerProfile> existing = database.players().find(uuid);
        if (existing.isPresent() && !options.overwrite()) {
            report.skipped(MigrationReport.Category.PLAYERS);
            return;
        }

        if (!options.dryRun()) {
            if (existing.isEmpty()) {
                database.players().upsert(uuid, name, skinOf(legacy), defaultToggles(), defaultBalance());
            }
            applyProfile(legacy, existing.orElse(null));
            cache.players().invalidate(uuid);
        }
        report.migrated(MigrationReport.Category.PLAYERS);

        writeNickname(legacy, name);
        writeHomes(legacy);
        writeBlocks(legacy);
        writeInventory(legacy);
        writeKitClaims(legacy);
    }

    private void applyProfile(LegacyPlayer legacy, PlayerProfile existing) {
        PlayerRepository players = database.players();
        UUID uuid = legacy.uuid();

        if (legacy.balance() != null) {
            players.updateBalance(uuid, legacy.balance());
        }
        if (!legacy.toggles().isEmpty()) {
            PlayerProfile.Toggles base = existing != null ? existing.toggles() : defaultToggles();
            players.updateToggles(uuid, legacy.toggles().merge(base));
        }
        if (legacy.godMode() != null) {
            players.updateGodMode(uuid, legacy.godMode());
        }
        if (legacy.flightAllowed() != null) {
            players.updateFlight(uuid, new PlayerProfile.Flight(legacy.flightAllowed(), false));
        }
        if (legacy.walkSpeed() != null) {
            players.updateWalkSpeed(uuid, Math.clamp(legacy.walkSpeed(), 0f, 1f));
        }
        if (legacy.flySpeed() != null) {
            players.updateFlySpeed(uuid, Math.clamp(legacy.flySpeed(), 0f, 1f));
        }
        if (legacy.playerTime() != null) {
            players.updatePlayerTime(uuid, legacy.playerTime());
        }
        if (legacy.playerWeather() != null) {
            players.updatePlayerWeather(uuid, legacy.playerWeather());
        }
        if (legacy.lastLocation() != null) {
            players.updateLastLocation(uuid, legacy.lastLocation());
        }
    }

    private PlayerProfile.Skin skinOf(LegacyPlayer legacy) {
        return legacy.skin() != null && legacy.skin().isPresent() ? legacy.skin() : PlayerProfile.Skin.EMPTY;
    }

    // Nicknames

    private void writeNickname(LegacyPlayer legacy, String name) {
        String raw = legacy.nickname();
        if (raw == null || raw.isBlank()) {
            return;
        }

        LegacyText.Converted converted = LegacyText.convert(raw);
        String value = converted.tagged();
        String plain = converted.plain();

        if (plain.isBlank()) {
            report.skipped(MigrationReport.Category.NICKNAMES);
            return;
        }
        if (value.length() > NICKNAME_MAX) {
            if (plain.length() > NICKNAME_MAX) {
                report.skipped(MigrationReport.Category.NICKNAMES);
                report.warn(name + ": nickname '" + plain + "' is longer than " + NICKNAME_MAX + " characters, skipped");
                return;
            }
            value = plain;
            report.warn(name + ": nickname colours dropped, the tagged form didn't fit in " + NICKNAME_MAX + " characters");
        }

        String normalized = plain.toLowerCase(Locale.ROOT);
        if (!claimedNicknames.add(normalized)) {
            report.skipped(MigrationReport.Category.NICKNAMES);
            report.warn(name + ": nickname '" + plain + "' was already taken earlier in this migration, skipped");
            return;
        }

        Optional<UUID> owner = database.players().findByNickname(normalized);
        if (owner.isPresent() && !owner.get().equals(legacy.uuid())) {
            report.skipped(MigrationReport.Category.NICKNAMES);
            report.warn(name + ": nickname '" + plain + "' is already used by " + owner.get() + ", skipped");
            return;
        }

        if (!options.dryRun()) {
            database.players().updateNickname(legacy.uuid(), value, normalized);
            cache.players().invalidate(legacy.uuid());
        }
        report.migrated(MigrationReport.Category.NICKNAMES);
    }

    // Homes

    private void writeHomes(LegacyPlayer legacy) {
        if (legacy.homes().isEmpty()) {
            return;
        }

        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean wrote = false;

        for (LegacyPlayer.Home home : legacy.homes()) {
            String name = home.name() == null ? "" : home.name().strip();
            if (name.isEmpty() || name.length() > HOME_NAME_MAX) {
                report.skipped(MigrationReport.Category.HOMES);
                report.warn(legacy.name() + ": home name '" + name + "' is unusable, skipped");
                continue;
            }
            if (!seen.add(name)) {
                // the new schema treats home names case-insensitively, the old ones didn't
                report.skipped(MigrationReport.Category.HOMES);
                report.warn(legacy.name() + ": duplicate home name '" + name + "', kept the first one");
                continue;
            }

            if (!options.dryRun()) {
                database.homes().save(new Home(legacy.uuid(), name, home.position(), null, false, home.createdAt()));
                wrote = true;
            }
            report.migrated(MigrationReport.Category.HOMES);
        }

        if (wrote) {
            cache.homes().invalidate(legacy.uuid());
        }
    }

    // Blocks

    private void writeBlocks(LegacyPlayer legacy) {
        if (legacy.blocked().isEmpty()) {
            return;
        }

        boolean wrote = false;
        for (UUID target : legacy.blocked()) {
            if (target.equals(legacy.uuid())) {
                report.skipped(MigrationReport.Category.BLOCKS);
                continue;
            }
            if (options.dryRun()) {
                report.migrated(MigrationReport.Category.BLOCKS);
                continue;
            }
            if (database.blocks().block(legacy.uuid(), target)) {
                report.migrated(MigrationReport.Category.BLOCKS);
                wrote = true;
            } else {
                report.skipped(MigrationReport.Category.BLOCKS);
            }
        }

        if (wrote) {
            cache.blocks().invalidate(legacy.uuid());
        }
    }

    // Inventories

    private void writeInventory(LegacyPlayer legacy) {
        LegacyPlayer.Inventory inventory = legacy.inventory();
        if (inventory == null) {
            return;
        }
        if (!options.dryRun()) {
            database.inventories().saveContents(legacy.uuid(), inventory.contents(), inventory.heldSlot());
            database.inventories().saveEnderChest(legacy.uuid(), inventory.enderChest());
        }
        report.migrated(MigrationReport.Category.INVENTORIES);
    }

    // Kit claims

    private void writeKitClaims(LegacyPlayer legacy) {
        if (legacy.kitClaims().isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        boolean wrote = false;

        for (LegacyPlayer.KitClaim claim : legacy.kitClaims()) {
            String key = kitCooldownKey(claim.kit());
            if (key.isEmpty() || key.length() > COOLDOWN_KEY_MAX) {
                report.skipped(MigrationReport.Category.KIT_CLAIMS);
                continue;
            }

            Instant expiresAt = claim.permanent() ? CooldownRepository.PERMANENT : claim.expiresAt();
            if (expiresAt == null || (!claim.permanent() && !expiresAt.isAfter(now))) {
                report.skipped(MigrationReport.Category.KIT_CLAIMS); // already lapsed, nothing worth carrying over
                continue;
            }

            if (!options.dryRun()) {
                database.cooldowns().set(legacy.uuid(), CooldownScope.KIT, key, "", expiresAt);
                wrote = true;
            }
            report.migrated(MigrationReport.Category.KIT_CLAIMS);
        }

        if (wrote) {
            cache.cooldowns().invalidate(legacy.uuid());
        }
    }

    private static String kitCooldownKey(String kitName) {
        return kitName == null ? "" : kitName.strip().toLowerCase(Locale.ROOT);
    }

    // Server data

    public void writeServerData(LegacyServerData data) {
        for (LegacyServerData.NamedPosition warp : data.warps()) {
            writeLocation(LocationRepository.Category.WARP, warp, MigrationReport.Category.WARPS);
        }
        for (LegacyServerData.NamedPosition spawn : data.spawns()) {
            writeLocation(LocationRepository.Category.SPAWN, spawn, MigrationReport.Category.SPAWNS);
        }
        for (LegacyServerData.Kit kit : data.kits()) {
            writeKit(kit);
        }
    }

    private void writeLocation(LocationRepository.Category category, LegacyServerData.NamedPosition entry,
                               MigrationReport.Category reportCategory) {
        String key = entry.key() == null ? "" : entry.key().strip();
        if (key.isEmpty() || key.length() > LOCATION_KEY_MAX) {
            report.failed(reportCategory, "unusable key '" + key + "'");
            return;
        }
        if (!options.overwrite() && database.locations().find(category, key).isPresent()) {
            report.skipped(reportCategory);
            return;
        }
        if (!options.dryRun()) {
            cache.locations().save(category, new NamedLocation(key, entry.position(), Instant.now(), null));
        }
        report.migrated(reportCategory);
    }

    private void writeKit(LegacyServerData.Kit legacy) {
        String name = legacy.name() == null ? "" : legacy.name().strip();
        if (name.isEmpty() || name.length() > KIT_NAME_MAX) {
            report.failed(MigrationReport.Category.KITS, "unusable kit name '" + name + "'");
            return;
        }
        if (!options.overwrite() && database.kits().find(name).isPresent()) {
            report.skipped(MigrationReport.Category.KITS);
            return;
        }

        ItemStack[] items = new ItemStack[Kit.TOTAL_SLOTS];
        int slot = 0;
        for (ItemStack item : legacy.items()) {
            if (slot >= Kit.MAIN_SIZE) {
                report.warn("kit '" + name + "' had more than " + Kit.MAIN_SIZE + " items, the extras were dropped");
                break;
            }
            items[slot++] = item;
        }

        Kit kit = new Kit(name, items, legacy.oneTime(), legacy.cooldownSeconds(),
                true, false, true, Instant.now(), null);

        if (!options.dryRun()) {
            cache.kits().save(kit);
        }
        report.migrated(MigrationReport.Category.KITS);
    }

    // Config defaults, mirroring PlayerService#handleJoin

    private double defaultBalance() {
        return configs.economy().balance().defaultBalance();
    }

    private PlayerProfile.Toggles defaultToggles() {
        MainConfig.ToggleDefaults defaults = configs.main().toggleDefaults();
        return new PlayerProfile.Toggles(
                defaults.msgToggle(), defaults.socialSpy(), defaults.chatToggle(), defaults.mentionToggle(),
                defaults.announceToggle(), defaults.payToggle(), defaults.payConfirmToggle(), defaults.tpToggle(),
                defaults.tpAutoAccept(), defaults.tpConfirmToggle()
        );
    }

    public void finish() {
        if (options.dryRun()) {
            return;
        }
        cache.players().invalidateAll();
        cache.blocks().invalidateAll();
    }
}