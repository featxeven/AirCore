package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.PersistentBossbarRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqlPersistentBossbarRepository implements PersistentBossbarRepository {

    private static final char ENTRY_SEPARATOR = '\u0001';
    private static final char KV_SEPARATOR = '\u0002';

    private final DataSource dataSource;
    private final String table;

    public SqlPersistentBossbarRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "persistent_bossbar";
    }

    @Override
    public List<State> findAll() {
        String sql = "SELECT * FROM " + table;
        List<State> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                results.add(mapState(result));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load persistent bossbar states", e);
        }
        return results;
    }

    @Override
    public void save(State state) {
        String insertSql = "INSERT INTO " + table
                + " (announcement_key, bar_text, duration_seconds, color, overlay, countdown, initial_progress, started_at, placeholders, sync_on_join, forced, conditions) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE announcement_key = ?")) {
                    delete.setString(1, state.key());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setString(1, state.key());
                    insert.setString(2, state.text());
                    insert.setInt(3, state.durationSeconds());
                    insert.setString(4, state.color());
                    insert.setString(5, state.overlay());
                    insert.setBoolean(6, state.countdown());
                    insert.setDouble(7, state.initialProgress());
                    insert.setLong(8, state.startedAtEpochMillis());
                    insert.setString(9, encodePlaceholders(state.placeholders()));
                    insert.setBoolean(10, state.syncOnJoin());
                    insert.setBoolean(11, state.force());
                    insert.setString(12, encodeList(state.conditions()));
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save persistent bossbar state '" + state.key() + "'", e);
        }
    }

    @Override
    public void clear(String key) {
        String sql = "DELETE FROM " + table + " WHERE announcement_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear persistent bossbar state '" + key + "'", e);
        }
    }

    private State mapState(ResultSet rs) throws SQLException {
        return new State(
                rs.getString("announcement_key"),
                rs.getString("bar_text"),
                rs.getInt("duration_seconds"),
                rs.getString("color"),
                rs.getString("overlay"),
                rs.getBoolean("countdown"),
                rs.getDouble("initial_progress"),
                rs.getLong("started_at"),
                decodePlaceholders(rs.getString("placeholders")),
                rs.getBoolean("sync_on_join"),
                rs.getBoolean("forced"),
                decodeList(rs.getString("conditions"))
        );
    }

    private String encodePlaceholders(Map<String, String> placeholders) {
        StringBuilder sb = new StringBuilder();
        placeholders.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append(ENTRY_SEPARATOR);
            sb.append(key).append(KV_SEPARATOR).append(value);
        });
        return sb.toString();
    }

    private Map<String, String> decodePlaceholders(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String entry : raw.split(String.valueOf(ENTRY_SEPARATOR))) {
            String[] parts = entry.split(String.valueOf(KV_SEPARATOR), 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }

    private String encodeList(List<String> values) {
        return String.join(String.valueOf(ENTRY_SEPARATOR), values);
    }

    private List<String> decodeList(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(List.of(raw.split(String.valueOf(ENTRY_SEPARATOR))));
    }
}