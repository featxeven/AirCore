package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.id.SqlSequence;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import org.bukkit.GameMode;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class SqlPlayerRepository implements PlayerRepository {

    private static final int IDENTITY_CHUNK = 1_000;

    private final DataSource dataSource;
    private final String table;
    private final String sequences;

    public SqlPlayerRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "players";
        this.sequences = tablePrefix + "id_sequences";
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        return findBy("uuid", uuid.toString());
    }

    @Override
    public Optional<PlayerProfile> findByName(String name) {
        String sql = "SELECT * FROM " + table + " WHERE LOWER(name) = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProfile(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load player by name " + name, e);
        }
    }

    @Override
    public Optional<UUID> findByNickname(String nickname) {
        String sql = "SELECT uuid FROM " + table + " WHERE nickname_normalized = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nickname);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(UUID.fromString(result.getString("uuid"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not look up player by nickname " + nickname, e);
        }
    }

    private Optional<PlayerProfile> findBy(String column, String value) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProfile(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load player by " + column + " " + value, e);
        }
    }

    @Override
    public Map<UUID, PlayerProfile> findAll(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        List<String> ids = uuids.stream().map(UUID::toString).toList();
        String placeholders = ids.stream().map(u -> "?").collect(Collectors.joining(", "));

        Map<UUID, PlayerProfile> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM " + table + " WHERE uuid IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setString(i + 1, ids.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PlayerProfile profile = mapProfile(rs);
                    result.put(profile.uuid(), profile);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not batch-load players", e);
        }
        return result;
    }

    @Override
    public Map<UUID, Identity> findIdentities(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(uuids.size(), "?"));
        String sql = "SELECT uuid, name, nickname, skin_value, skin_signature " +
                "FROM " + table + " WHERE uuid IN (" + placeholders + ")";

        Map<UUID, Identity> result = new LinkedHashMap<>(uuids.size());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (UUID uuid : uuids) {
                statement.setString(index++, uuid.toString());
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Identity identity = mapIdentity(rs);
                    result.put(identity.uuid(), identity);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load player identities", e);
        }
        return result;
    }

    @Override
    public Set<UUID> findAllUuids() {
        Set<UUID> uuids = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM " + table);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                uuids.add(UUID.fromString(result.getString("uuid")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load all player UUIDs", e);
        }
        return uuids;
    }

    @Override
    public JoinResult upsert(UUID uuid, String name, PlayerProfile.Skin skin, PlayerProfile.Toggles defaultToggles, double defaultBalance) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PlayerProfile> existing = findForUpdate(connection, uuid);
                PlayerProfile profile = existing.isPresent()
                        ? refreshIdentity(connection, existing.get(), name, skin)
                        : insertNew(connection, uuid, name, skin, defaultToggles, defaultBalance);
                connection.commit();
                return new JoinResult(profile, existing.isEmpty());
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not upsert player " + uuid + " on join", e);
        }
    }

    private Optional<PlayerProfile> findForUpdate(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapProfile(result)) : Optional.empty();
            }
        }
    }

    private PlayerProfile refreshIdentity(Connection connection, PlayerProfile current, String name, PlayerProfile.Skin skin) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table + " SET name = ?, skin_value = ?, skin_signature = ? WHERE uuid = ?")) {
            statement.setString(1, name);
            statement.setString(2, skin.value());
            statement.setString(3, skin.signature());
            statement.setString(4, current.uuid().toString());
            statement.executeUpdate();
        }
        return new PlayerProfile(current.uuid(), name, current.nickname(), skin, current.joinNumber(),
                current.firstJoinAt(), current.lastSeenAt(), current.lastLocation(), current.chatChannel(),
                current.gameMode(), current.godMode(), current.flight(), current.walkSpeed(), current.flySpeed(),
                current.playerTime(), current.playerWeather(), current.toggles(), current.pendingPayment(), current.balance());
    }

    private PlayerProfile insertNew(Connection connection, UUID uuid, String name, PlayerProfile.Skin skin, PlayerProfile.Toggles toggles, double defaultBalance) throws SQLException {
        int joinNumber = SqlSequence.next(connection, sequences, "player_join_number");
        Instant now = Instant.now();
        PlayerProfile profile = new PlayerProfile(uuid, name, null, skin, joinNumber, now, now, null, null,
                GameMode.SURVIVAL, false, PlayerProfile.Flight.GROUNDED, 0.2f, 0.1f, null, null, toggles, 0d, defaultBalance);

        String sql = "INSERT INTO " + table
                + " (uuid, name, skin_value, skin_signature, join_number, first_join_at, last_seen_at, "
                + "game_mode, god_mode, allow_flight, flying, walk_speed, fly_speed, toggles, balance) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setString(3, skin.value());
            statement.setString(4, skin.signature());
            statement.setInt(5, joinNumber);
            statement.setLong(6, now.toEpochMilli());
            statement.setLong(7, now.toEpochMilli());
            statement.setString(8, GameMode.SURVIVAL.name());
            statement.setBoolean(9, false);
            statement.setBoolean(10, false);
            statement.setBoolean(11, false);
            statement.setFloat(12, 0.2f);
            statement.setFloat(13, 0.1f);
            statement.setInt(14, toggles.toBits());
            statement.setDouble(15, defaultBalance);
            statement.executeUpdate();
        }
        return profile;
    }

    @Override
    public void updateLastSeen(UUID uuid, Instant lastSeenAt) {
        update(uuid, "last_seen_at = ?", statement -> statement.setLong(1, lastSeenAt.toEpochMilli()));
    }

    @Override
    public void updateLastLocation(UUID uuid, Position location) {
        String sql = "UPDATE " + table + " SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ? WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location.world());
            statement.setDouble(2, location.x());
            statement.setDouble(3, location.y());
            statement.setDouble(4, location.z());
            statement.setFloat(5, location.yaw());
            statement.setFloat(6, location.pitch());
            statement.setString(7, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update last location for player " + uuid, e);
        }
    }

    @Override
    public void updateNickname(UUID uuid, @Nullable String nickname, @Nullable String normalizedNickname) {
        String sql = "UPDATE " + table + " SET nickname = ?, nickname_normalized = ? WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nickname);
            statement.setString(2, normalizedNickname);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update nickname for player " + uuid, e);
        }
    }

    @Override
    public void updateChatChannel(UUID uuid, @Nullable String channel) {
        update(uuid, "chat_channel = ?", statement -> statement.setString(1, channel));
    }

    @Override
    public void updateGameMode(UUID uuid, GameMode gameMode) {
        update(uuid, "game_mode = ?", statement -> statement.setString(1, gameMode.name()));
    }

    @Override
    public void updateGodMode(UUID uuid, boolean enabled) {
        update(uuid, "god_mode = ?", statement -> statement.setBoolean(1, enabled));
    }

    @Override
    public void updateFlight(UUID uuid, PlayerProfile.Flight flight) {
        String sql = "UPDATE " + table + " SET allow_flight = ?, flying = ? WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, flight.allowed());
            statement.setBoolean(2, flight.flying());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update flight state for player " + uuid, e);
        }
    }

    @Override
    public void updateWalkSpeed(UUID uuid, float walkSpeed) {
        update(uuid, "walk_speed = ?", statement -> statement.setFloat(1, walkSpeed));
    }

    @Override
    public void updateFlySpeed(UUID uuid, float flySpeed) {
        update(uuid, "fly_speed = ?", statement -> statement.setFloat(1, flySpeed));
    }

    @Override
    public void updatePlayerTime(UUID uuid, @Nullable Integer ticks) {
        update(uuid, "player_time = ?", statement -> {
            if (ticks != null) statement.setInt(1, ticks);
            else statement.setNull(1, Types.INTEGER);
        });
    }

    @Override
    public void updatePlayerWeather(UUID uuid, @Nullable PlayerProfile.PersonalWeather weather) {
        update(uuid, "player_weather = ?", statement -> statement.setString(1, weather != null ? weather.name() : null));
    }

    @Override
    public void updateToggles(UUID uuid, PlayerProfile.Toggles toggles) {
        update(uuid, "toggles = ?", statement -> statement.setInt(1, toggles.toBits()));
    }

    @Override
    public void updateBalance(UUID uuid, double balance) {
        update(uuid, "balance = ?", statement -> statement.setDouble(1, balance));
    }

    @Override
    public void addBalance(UUID uuid, double amount) {
        update(uuid, "balance = balance + ?", statement -> statement.setDouble(1, amount));
    }

    @Override
    public void addPendingPayment(UUID uuid, double amount) {
        update(uuid, "pending_payment = pending_payment + ?", statement -> statement.setDouble(1, amount));
    }

    @Override
    public void clearPendingPayment(UUID uuid) {
        update(uuid, "pending_payment = ?", statement -> statement.setDouble(1, 0.0));
    }

    @Override
    public double totalBalance() {
        String sql = "SELECT SUM(balance) AS total FROM " + table;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getDouble("total") : 0.0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not compute total player balance", e);
        }
    }

    @Override
    public List<BalanceEntry> topBalances(boolean descending, int limit, double minValue) {
        String direction = descending ? "DESC" : "ASC";
        String sql = "SELECT uuid, name, nickname, skin_value, skin_signature, balance " +
                "FROM " + table + " " +
                "WHERE balance >= ? " +
                "ORDER BY balance " + direction + ", uuid " + direction +
                (limit > 0 ? " LIMIT ?" : "");

        List<BalanceEntry> ranked = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, minValue);
            if (limit > 0) {
                statement.setInt(2, limit);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ranked.add(new BalanceEntry(mapIdentity(rs), rs.getDouble("balance")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load top balances", e);
        }
        return ranked;
    }

    @Override
    public int deleteAll(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return 0;
        }
        List<String> ids = uuids.stream().map(UUID::toString).toList();
        String placeholders = ids.stream().map(u -> "?").collect(Collectors.joining(", "));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setString(i + 1, ids.get(i));
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not bulk-delete players", e);
        }
    }

    // Bulk balance operations

    @Override
    public int addBalanceToAll(double amount, double min, double max) {
        if (amount >= 0 && max != -1) {
            return updateAllBalances(
                    "CASE WHEN balance >= ? THEN balance WHEN balance + ? > ? THEN ? ELSE balance + ? END",
                    max, amount, max, max, amount);
        }
        if (amount < 0 && min != -1) {
            return updateAllBalances(
                    "CASE WHEN balance <= ? THEN balance WHEN balance + ? < ? THEN ? ELSE balance + ? END",
                    min, amount, min, min, amount);
        }
        return updateAllBalances("balance + ?", amount);
    }

    @Override
    public int setBalanceForAll(double balance) {
        return updateAllBalances("?", balance);
    }

    @Override
    public int resetBalanceForAll(double defaultBalance) {
        return setBalanceForAll(defaultBalance);
    }

    private int updateAllBalances(String expression, double... values) {
        String sql = "UPDATE " + table + " SET balance = " + expression;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setDouble(i + 1, values[i]);
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not bulk-update balances", e);
        }
    }

    // Shared single-column update helper

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private void update(UUID uuid, String assignment, Binder binder) {
        String sql = "UPDATE " + table + " SET " + assignment + " WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update player " + uuid, e);
        }
    }

    // Row mapping

    private Identity mapIdentity(ResultSet rs) throws SQLException {
        return new Identity(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("nickname"),
                new PlayerProfile.Skin(rs.getString("skin_value"), rs.getString("skin_signature")));
    }

    private PlayerProfile mapProfile(ResultSet rs) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("nickname"),
                new PlayerProfile.Skin(rs.getString("skin_value"), rs.getString("skin_signature")),
                rs.getInt("join_number"),
                Instant.ofEpochMilli(rs.getLong("first_join_at")),
                Instant.ofEpochMilli(rs.getLong("last_seen_at")),
                mapLocation(rs),
                rs.getString("chat_channel"),
                GameMode.valueOf(rs.getString("game_mode")),
                rs.getBoolean("god_mode"),
                new PlayerProfile.Flight(rs.getBoolean("allow_flight"), rs.getBoolean("flying")),
                rs.getFloat("walk_speed"),
                rs.getFloat("fly_speed"),
                getNullableInt(rs, "player_time"),
                mapWeather(rs.getString("player_weather")),
                PlayerProfile.Toggles.fromBits(rs.getInt("toggles")),
                rs.getDouble("pending_payment"),
                rs.getDouble("balance")
        );
    }

    private Position mapLocation(ResultSet rs) throws SQLException {
        String world = rs.getString("last_world");
        if (world == null) {
            return null;
        }
        return new Position(world, rs.getDouble("last_x"), rs.getDouble("last_y"), rs.getDouble("last_z"),
                rs.getFloat("last_yaw"), rs.getFloat("last_pitch"));
    }

    private PlayerProfile.PersonalWeather mapWeather(String raw) {
        return raw != null ? PlayerProfile.PersonalWeather.valueOf(raw) : null;
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}