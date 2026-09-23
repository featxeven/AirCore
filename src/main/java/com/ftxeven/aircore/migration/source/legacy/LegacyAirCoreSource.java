package com.ftxeven.aircore.migration.source.legacy;

import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.migration.MigrationSource;
import com.ftxeven.aircore.migration.model.LegacyPlayer;
import com.ftxeven.aircore.migration.model.LegacyServerData;
import com.ftxeven.aircore.migration.util.MigrationLocations;
import com.ftxeven.aircore.model.PlayerInventory;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LegacyAirCoreSource implements MigrationSource {

    @Override
    public String id() {
        return "legacy";
    }

    @Override
    public String displayName() {
        return "AirCore 1.x";
    }

    @Override
    public String defaultPath() {
        return "plugins/database.db";
    }

    @Override
    public String pathHint() {
        return "Copy the old database.db (and optionally kits.yml, warps.yml and spawn.yml) somewhere the server "
                + "can read, then run /aircore migrate legacy plugins/database.db";
    }

    @Override
    public List<String> notMigrated() {
        return List.of("per-command cooldowns (the old ones were keyed by raw command text)",
                "kit auto-equip (no equivalent setting)", "afk state and back locations");
    }

    @Override
    public Reader open(Path path) throws Exception {
        Path database = resolveDatabase(path);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // modern drivers register themselves
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        return new LegacyReader(connection, database.getParent());
    }

    private Path resolveDatabase(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return path;
        }
        if (Files.isDirectory(path)) {
            for (Path candidate : List.of(path.resolve("database.db"), path.resolve("data").resolve("database.db"))) {
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IOException("No database.db found at " + path);
    }

    private static final class LegacyReader implements Reader {

        private final Connection connection;
        private final Path base;
        private final Set<String> recordColumns = new HashSet<>();

        private LegacyReader(Connection connection, Path base) throws SQLException {
            this.connection = connection;
            this.base = base;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "player_records", null)) {
                while (columns.next()) {
                    recordColumns.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (recordColumns.isEmpty()) {
                throw new SQLException("This database has no player_records table, it isn't an AirCore 1.x database");
            }
        }

        @Override
        public List<Candidate> players() throws SQLException {
            List<Candidate> candidates = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT join_index, uuid, name FROM player_records ORDER BY join_index ASC")) {
                while (result.next()) {
                    UUID uuid = parseUuid(result.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    String name = result.getString("name");
                    candidates.add(new Candidate(uuid, name == null ? "" : name,
                            result.getLong("join_index"), uuid + " (" + name + ")"));
                }
            }
            return candidates;
        }

        @Override
        public Optional<LegacyPlayer> load(Candidate candidate) throws SQLException {
            UUID uuid = candidate.uuid();

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_records WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }

                    String name = result.getString("name");
                    if (name == null || name.isBlank()) {
                        return Optional.empty();
                    }

                    LegacyPlayer.Builder builder = LegacyPlayer.builder(uuid, name.strip())
                            .skin(skin(result))
                            .balance(nullableDouble(result, "balance"))
                            .nickname(nullableString(result, "nick"))
                            .godMode(nullableBoolean(result, "god_enabled"))
                            .flightAllowed(nullableBoolean(result, "fly_enabled"))
                            .walkSpeed(speed(result, "walk_speed", 0.2f))
                            .flySpeed(speed(result, "fly_speed", 0.1f))
                            .playerTime(playerTime(result))
                            .playerWeather(weather(nullableString(result, "player_weather")))
                            .lastLocation(position(result))
                            .toggle(ToggleKey.CHAT, nullableBoolean(result, "chat_enabled"))
                            .toggle(ToggleKey.MENTIONS, nullableBoolean(result, "mentions_enabled"))
                            .toggle(ToggleKey.MSG, nullableBoolean(result, "pm_enabled"))
                            .toggle(ToggleKey.SOCIAL_SPY, nullableBoolean(result, "socialspy_enabled"))
                            .toggle(ToggleKey.PAY, nullableBoolean(result, "pay_enabled"))
                            .toggle(ToggleKey.TP, nullableBoolean(result, "teleport_enabled"))
                            .toggle(ToggleKey.ANNOUNCEMENTS, nullableBoolean(result, "announcements_enabled"));

                    readHomes(uuid, builder);
                    readBlocks(uuid, builder);
                    readKitClaims(uuid, builder);
                    builder.inventory(readInventory(uuid));

                    return Optional.of(builder.build());
                }
            }
        }

        private void readHomes(UUID uuid, LegacyPlayer.Builder builder) throws SQLException {
            String sql = "SELECT name, world, x, y, z, yaw, pitch, created_at FROM player_homes WHERE uuid = ? ORDER BY created_at ASC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String world = result.getString("world");
                        if (world == null || world.isBlank()) {
                            continue;
                        }
                        Position position = new Position(world,
                                result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                                result.getFloat("yaw"), result.getFloat("pitch"));
                        builder.home(new LegacyPlayer.Home(result.getString("name"), position,
                                instant(result.getLong("created_at"))));
                    }
                }
            }
        }

        private void readBlocks(UUID uuid, LegacyPlayer.Builder builder) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT blocked_uuid FROM player_blocks WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID blocked = parseUuid(result.getString("blocked_uuid"));
                        if (blocked != null) {
                            builder.blocked(blocked);
                        }
                    }
                }
            }
        }

        private void readKitClaims(UUID uuid, LegacyPlayer.Builder builder) throws SQLException {
            String sql = "SELECT kit, last_claim, one_time_claimed, last_cooldown FROM player_kits WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String kit = result.getString("kit");
                        if (kit == null || kit.isBlank()) {
                            continue;
                        }
                        if (result.getInt("one_time_claimed") != 0) {
                            builder.kitClaim(new LegacyPlayer.KitClaim(kit, null, true));
                            continue;
                        }
                        long cooldown = result.getLong("last_cooldown");
                        if (cooldown > 0) {
                            builder.kitClaim(new LegacyPlayer.KitClaim(kit,
                                    instant(result.getLong("last_claim")).plusSeconds(cooldown), false));
                        }
                    }
                }
            }
        }

        private @Nullable LegacyPlayer.Inventory readInventory(UUID uuid) throws SQLException {
            String sql = "SELECT contents, armor, offhand, enderchest FROM player_inventories WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }

                    ItemStack[] storage = LegacyItemBlobs.decode(result.getBytes("contents"));
                    ItemStack[] armor = LegacyItemBlobs.decode(result.getBytes("armor"));
                    ItemStack[] offhand = LegacyItemBlobs.decode(result.getBytes("offhand"));
                    ItemStack[] ender = LegacyItemBlobs.decode(result.getBytes("enderchest"));

                    // bukkit layout: 0-35 storage, 36-39 armor (boots, leggings, chestplate, helmet), 40 offhand
                    ItemStack[] contents = new ItemStack[PlayerInventory.MAIN_SIZE];
                    LegacyItemBlobs.copy(storage, 0, contents, 0, 36);
                    LegacyItemBlobs.copy(armor, 0, contents, 36, 4);
                    if (offhand.length > 0) {
                        contents[40] = offhand[0];
                    }

                    ItemStack[] enderChest = new ItemStack[PlayerInventory.ENDERCHEST_SIZE];
                    LegacyItemBlobs.copy(ender, 0, enderChest, 0, Math.min(ender.length, PlayerInventory.ENDERCHEST_SIZE));

                    return new LegacyPlayer.Inventory(contents, enderChest, 0);
                }
            }
        }

        @Override
        public LegacyServerData serverData() {
            return new LegacyServerData(warps(), spawns(), kits());
        }

        private List<LegacyServerData.NamedPosition> warps() {
            YamlConfiguration yaml = yaml("warps.yml");
            ConfigurationSection warps = yaml == null ? null : yaml.getConfigurationSection("warps");
            if (warps == null) {
                return List.of();
            }

            List<LegacyServerData.NamedPosition> result = new ArrayList<>();
            for (String key : warps.getKeys(false)) {
                MigrationLocations.read(warps.getConfigurationSection(key))
                        .ifPresent(position -> result.add(new LegacyServerData.NamedPosition(key, position)));
            }
            return result;
        }

        private List<LegacyServerData.NamedPosition> spawns() {
            YamlConfiguration yaml = yaml("spawn.yml");
            if (yaml == null) {
                return List.of();
            }
            return MigrationLocations.read(yaml.getConfigurationSection("spawn"))
                    .map(position -> List.of(new LegacyServerData.NamedPosition(LocationRepository.SPAWN_DEFAULT, position)))
                    .orElse(List.of());
        }

        private List<LegacyServerData.Kit> kits() {
            YamlConfiguration yaml = yaml("kits.yml");
            ConfigurationSection kits = yaml == null ? null : yaml.getConfigurationSection("kits");
            if (kits == null) {
                return List.of();
            }

            List<LegacyServerData.Kit> result = new ArrayList<>();
            for (String name : kits.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                List<?> serialized = kits.getList(name + ".items");
                if (serialized != null) {
                    for (Object entry : serialized) {
                        if (!(entry instanceof Map<?, ?> map)) {
                            continue;
                        }
                        try {
                            @SuppressWarnings("unchecked")
                            ItemStack item = ItemStack.deserialize((Map<String, Object>) map);
                            if (!item.getType().isAir() && item.getAmount() > 0) {
                                items.add(item);
                            }
                        } catch (Exception ignored) {
                            // unreadable item definitions are dropped rather than failing the whole kit
                        }
                    }
                }
                if (items.isEmpty()) {
                    continue;
                }

                long cooldown = kits.getLong(name + ".cooldown", 0);
                Integer cooldownSeconds = cooldown > 0 ? (int) Math.min(cooldown, Integer.MAX_VALUE) : null;
                result.add(new LegacyServerData.Kit(name, items, kits.getBoolean(name + ".one-time", false), cooldownSeconds));
            }
            return result;
        }

        // the admin may have copied database.db on its own, or the whole old data folder
        private @Nullable YamlConfiguration yaml(String fileName) {
            for (Path candidate : List.of(base.resolve(fileName), base.resolve("data").resolve(fileName))) {
                if (Files.isRegularFile(candidate)) {
                    return YamlConfiguration.loadConfiguration(candidate.toFile());
                }
            }
            return null;
        }

        @Override
        public void close() {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing useful to do while tearing down
            }
        }

        // Column helpers

        private boolean has(String column) {
            return recordColumns.contains(column);
        }

        private @Nullable String nullableString(ResultSet result, String column) throws SQLException {
            if (!has(column)) {
                return null;
            }
            String value = result.getString(column);
            return value == null || value.isBlank() ? null : value;
        }

        private @Nullable Double nullableDouble(ResultSet result, String column) throws SQLException {
            if (!has(column)) {
                return null;
            }
            double value = result.getDouble(column);
            return result.wasNull() ? null : value;
        }

        private @Nullable Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
            if (!has(column)) {
                return null;
            }
            int value = result.getInt(column);
            return result.wasNull() ? null : value != 0;
        }

        private @Nullable Float speed(ResultSet result, String column, float bukkitBase) throws SQLException {
            Double multiplier = nullableDouble(result, column);
            if (multiplier == null) {
                return null;
            }
            return Math.clamp((float) (multiplier * bukkitBase), 0f, 1f);
        }

        private @Nullable Integer playerTime(ResultSet result) throws SQLException {
            if (!has("player_time")) {
                return null;
            }
            long ticks = result.getLong("player_time");
            if (result.wasNull() || ticks < 0 || ticks > Integer.MAX_VALUE) {
                return null; // 1.x used -1 for "synced with the world"
            }
            return (int) ticks;
        }

        private @Nullable PlayerProfile.PersonalWeather weather(@Nullable String raw) {
            if (raw == null) {
                return null;
            }
            return switch (raw.toUpperCase(Locale.ROOT)) {
                case "CLEAR" -> PlayerProfile.PersonalWeather.CLEAR;
                case "DOWNFALL", "RAIN", "THUNDER" -> PlayerProfile.PersonalWeather.THUNDER;
                default -> null;
            };
        }

        private @Nullable PlayerProfile.Skin skin(ResultSet result) throws SQLException {
            String value = nullableString(result, "skin_value");
            String signature = nullableString(result, "skin_signature");
            return value != null && signature != null ? new PlayerProfile.Skin(value, signature) : null;
        }

        private @Nullable Position position(ResultSet result) throws SQLException {
            String world = nullableString(result, "world");
            if (world == null) {
                return null;
            }
            return new Position(world, result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                    result.getFloat("yaw"), result.getFloat("pitch"));
        }

        // 1.x stored epoch seconds; be forgiving in case anything ever wrote millis
        private static Instant instant(long stored) {
            if (stored <= 0) {
                return Instant.now();
            }
            return stored > 100_000_000_000L ? Instant.ofEpochMilli(stored) : Instant.ofEpochSecond(stored);
        }

        private static @Nullable UUID parseUuid(@Nullable String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}