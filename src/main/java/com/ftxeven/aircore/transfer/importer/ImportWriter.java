package com.ftxeven.aircore.transfer.importer;

import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.transfer.TransferOptions;
import com.ftxeven.aircore.transfer.TransferReport;
import com.ftxeven.aircore.transfer.importer.model.ImportedPlayer;
import com.ftxeven.aircore.transfer.importer.model.ImportedServerData;
import com.ftxeven.aircore.transfer.importer.util.LegacyText;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.model.PlayerProfile;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class ImportWriter {

    private static final int NAME_MAX = 16;
    private static final int NICKNAME_MAX = 32;
    private static final int HOME_NAME_MAX = 32;
    private static final int KIT_NAME_MAX = 32;
    private static final int LOCATION_KEY_MAX = 64;
    private static final int COOLDOWN_KEY_MAX = 64;

    private final DatabaseManager database;
    private final CacheManager cache;
    private final ConfigManager configs;
    private final TransferOptions options;
    private final TransferReport report;

    private final Set<String> claimedNicknames = new HashSet<>();

    public ImportWriter(DatabaseManager database, CacheManager cache, ConfigManager configs,
                        TransferOptions options, TransferReport report) {
        this.database = database;
        this.cache = cache;
        this.configs = configs;
        this.options = options;
        this.report = report;
    }

    // Players

    public void writePlayer(ImportedPlayer imported) {
        UUID uuid = imported.uuid();
        String name = clean(imported.name());

        if (unusable(name, NAME_MAX)) {
            report.failed(TransferReport.Category.PLAYERS, uuid + ": unusable account name '" + name + "'");
            return;
        }

        Optional<PlayerProfile> existing = database.players().find(uuid);
        if (existing.isPresent() && !options.overwrite()) {
            report.skipped(TransferReport.Category.PLAYERS);
            return;
        }

        if (!options.dryRun()) {
            if (existing.isEmpty()) {
                database.players().upsert(uuid, name, skinOf(imported), defaultToggles(), defaultBalance());
            }
            applyProfile(imported, existing.orElse(null));
            cache.players().invalidate(uuid);
        }
        report.migrated(TransferReport.Category.PLAYERS);

        writeNickname(imported, name);
        writeHomes(imported);
        writeBlocks(imported);
        writeInventory(imported);
        writeKitClaims(imported);
    }

    private void applyProfile(ImportedPlayer imported, PlayerProfile existing) {
        PlayerRepository players = database.players();
        UUID uuid = imported.uuid();

        if (imported.balance() != null) {
            players.updateBalance(uuid, imported.balance());
        }
        if (!imported.toggles().isEmpty()) {
            PlayerProfile.Toggles base = existing != null ? existing.toggles() : defaultToggles();
            players.updateToggles(uuid, mergeToggles(base, imported.toggles()));
        }
        if (imported.godMode() != null) {
            players.updateGodMode(uuid, imported.godMode());
        }
        if (imported.flightAllowed() != null) {
            players.updateFlight(uuid, new PlayerProfile.Flight(imported.flightAllowed(), false));
        }
        if (imported.walkSpeed() != null) {
            players.updateWalkSpeed(uuid, Math.clamp(imported.walkSpeed(), 0f, 1f));
        }
        if (imported.flySpeed() != null) {
            players.updateFlySpeed(uuid, Math.clamp(imported.flySpeed(), 0f, 1f));
        }
        if (imported.playerTime() != null) {
            players.updatePlayerTime(uuid, imported.playerTime());
        }
        if (imported.playerWeather() != null) {
            players.updatePlayerWeather(uuid, imported.playerWeather());
        }
        if (imported.lastLocation() != null) {
            players.updateLastLocation(uuid, imported.lastLocation());
        }
    }

    // only the toggles the source had a value for are overridden, the rest keep their current value
    private static PlayerProfile.Toggles mergeToggles(PlayerProfile.Toggles base, Map<ToggleKey, Boolean> values) {
        PlayerProfile.Toggles result = base;
        for (Map.Entry<ToggleKey, Boolean> entry : values.entrySet()) {
            result = entry.getKey().with(result, entry.getValue());
        }
        return result;
    }

    private PlayerProfile.Skin skinOf(ImportedPlayer imported) {
        return imported.skin() != null && imported.skin().isPresent() ? imported.skin() : PlayerProfile.Skin.EMPTY;
    }

    // Nicknames

    private void writeNickname(ImportedPlayer imported, String name) {
        String raw = imported.nickname();
        if (raw == null || raw.isBlank()) {
            return;
        }

        LegacyText.Converted converted = LegacyText.convert(raw);
        String value = converted.tagged();
        String plain = converted.plain();

        if (plain.isBlank()) {
            report.skipped(TransferReport.Category.NICKNAMES);
            return;
        }
        if (value.length() > NICKNAME_MAX) {
            if (plain.length() > NICKNAME_MAX) {
                report.skipped(TransferReport.Category.NICKNAMES);
                report.warn(name + ": nickname '" + plain + "' is longer than " + NICKNAME_MAX + " characters, skipped");
                return;
            }
            value = plain;
            report.warn(name + ": nickname colours dropped, the tagged form didn't fit in " + NICKNAME_MAX + " characters");
        }

        String normalized = plain.toLowerCase(Locale.ROOT);
        if (!claimedNicknames.add(normalized)) {
            report.skipped(TransferReport.Category.NICKNAMES);
            report.warn(name + ": nickname '" + plain + "' was already taken earlier in this transfer, skipped");
            return;
        }

        Optional<UUID> owner = database.players().findByNickname(normalized);
        if (owner.isPresent() && !owner.get().equals(imported.uuid())) {
            report.skipped(TransferReport.Category.NICKNAMES);
            report.warn(name + ": nickname '" + plain + "' is already used by " + owner.get() + ", skipped");
            return;
        }

        if (!options.dryRun()) {
            database.players().updateNickname(imported.uuid(), value, normalized);
            cache.players().invalidate(imported.uuid());
        }
        report.migrated(TransferReport.Category.NICKNAMES);
    }

    // Homes

    private void writeHomes(ImportedPlayer imported) {
        if (imported.homes().isEmpty()) {
            return;
        }

        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean wrote = false;

        for (ImportedPlayer.Home home : imported.homes()) {
            String name = clean(home.name());
            if (unusable(name, HOME_NAME_MAX)) {
                report.skipped(TransferReport.Category.HOMES);
                report.warn(imported.name() + ": home name '" + name + "' is unusable, skipped");
                continue;
            }
            if (!seen.add(name)) {
                // the new migration treats home names case-insensitively, the old ones didn't
                report.skipped(TransferReport.Category.HOMES);
                report.warn(imported.name() + ": duplicate home name '" + name + "', kept the first one");
                continue;
            }

            if (!options.dryRun()) {
                database.homes().save(new Home(imported.uuid(), name, home.position(), null, false, home.createdAt()));
                wrote = true;
            }
            report.migrated(TransferReport.Category.HOMES);
        }

        if (wrote) {
            cache.homes().invalidate(imported.uuid());
        }
    }

    // Blocks

    private void writeBlocks(ImportedPlayer imported) {
        if (imported.blocked().isEmpty()) {
            return;
        }

        boolean wrote = false;
        for (UUID target : imported.blocked()) {
            if (target.equals(imported.uuid())) {
                report.skipped(TransferReport.Category.BLOCKS);
                continue;
            }
            if (options.dryRun()) {
                report.migrated(TransferReport.Category.BLOCKS);
                continue;
            }
            if (database.blocks().block(imported.uuid(), target)) {
                report.migrated(TransferReport.Category.BLOCKS);
                wrote = true;
            } else {
                report.skipped(TransferReport.Category.BLOCKS);
            }
        }

        if (wrote) {
            cache.blocks().invalidate(imported.uuid());
        }
    }

    // Inventories

    private void writeInventory(ImportedPlayer imported) {
        ImportedPlayer.Inventory inventory = imported.inventory();
        if (inventory == null) {
            return;
        }
        if (!options.dryRun()) {
            database.inventories().saveContents(imported.uuid(), inventory.contents(), inventory.heldSlot());
            database.inventories().saveEnderChest(imported.uuid(), inventory.enderChest());
        }
        report.migrated(TransferReport.Category.INVENTORIES);
    }

    // Kit claims

    private void writeKitClaims(ImportedPlayer imported) {
        if (imported.kitClaims().isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        boolean wrote = false;

        for (ImportedPlayer.KitClaim claim : imported.kitClaims()) {
            String key = kitCooldownKey(claim.kit());
            if (unusable(key, COOLDOWN_KEY_MAX)) {
                report.skipped(TransferReport.Category.KIT_CLAIMS);
                continue;
            }

            Instant expiresAt = claim.permanent() ? CooldownRepository.PERMANENT : claim.expiresAt();
            if (expiresAt == null || (!claim.permanent() && !expiresAt.isAfter(now))) {
                report.skipped(TransferReport.Category.KIT_CLAIMS); // already lapsed, nothing worth carrying over
                continue;
            }

            if (!options.dryRun()) {
                database.cooldowns().set(imported.uuid(), CooldownScope.KIT, key, "", expiresAt);
                wrote = true;
            }
            report.migrated(TransferReport.Category.KIT_CLAIMS);
        }

        if (wrote) {
            cache.cooldowns().invalidate(imported.uuid());
        }
    }

    private static String kitCooldownKey(@Nullable String kitName) {
        return clean(kitName).toLowerCase(Locale.ROOT);
    }

    // Server data

    public void writeServerData(ImportedServerData data) {
        for (ImportedServerData.NamedPosition warp : data.warps()) {
            writeLocation(LocationRepository.Category.WARP, warp, TransferReport.Category.WARPS);
        }
        for (ImportedServerData.NamedPosition spawn : data.spawns()) {
            writeLocation(LocationRepository.Category.SPAWN, spawn, TransferReport.Category.SPAWNS);
        }
        for (ImportedServerData.Kit kit : data.kits()) {
            writeKit(kit);
        }
    }

    private void writeLocation(LocationRepository.Category category, ImportedServerData.NamedPosition entry,
                               TransferReport.Category reportCategory) {
        String key = clean(entry.key());
        if (unusable(key, LOCATION_KEY_MAX)) {
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

    private void writeKit(ImportedServerData.Kit imported) {
        String name = clean(imported.name());
        if (unusable(name, KIT_NAME_MAX)) {
            report.failed(TransferReport.Category.KITS, "unusable kit name '" + name + "'");
            return;
        }
        if (!options.overwrite() && database.kits().find(name).isPresent()) {
            report.skipped(TransferReport.Category.KITS);
            return;
        }

        ItemStack[] items = new ItemStack[Kit.TOTAL_SLOTS];
        int slot = 0;
        for (ItemStack item : imported.items()) {
            if (slot >= Kit.MAIN_SIZE) {
                report.warn("kit '" + name + "' had more than " + Kit.MAIN_SIZE + " items, the events were dropped");
                break;
            }
            items[slot++] = item;
        }

        Kit kit = new Kit(name, items, imported.oneTime(), imported.cooldownSeconds(),
                true, false, true, Instant.now(), null);

        if (!options.dryRun()) {
            cache.kits().save(kit);
        }
        report.migrated(TransferReport.Category.KITS);
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

    // Validation helpers

    // null-safe strip, used before every name/key length check
    private static String clean(@Nullable String raw) {
        return raw == null ? "" : raw.strip();
    }

    private static boolean unusable(String value, int max) {
        return value.isEmpty() || value.length() > max;
    }
}