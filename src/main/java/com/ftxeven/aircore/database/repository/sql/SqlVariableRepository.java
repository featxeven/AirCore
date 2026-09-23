package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.VariableRepository;
import com.ftxeven.aircore.model.Variable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SqlVariableRepository implements VariableRepository {

    private static final int CHUNK = 1_000;

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private final DataSource dataSource;
    private final String table;
    private final String upsertSql;
    private final String deleteSql;

    public SqlVariableRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "variables";
        String insert = "INSERT INTO " + table + " (owner, var_key, value, numeric_value) VALUES (?, ?, ?, ?)";
        this.upsertSql = isSqlite(dataSource)
                ? insert + " ON CONFLICT(owner, var_key) DO UPDATE SET value = excluded.value, numeric_value = excluded.numeric_value"
                : insert + " ON DUPLICATE KEY UPDATE value = VALUES(value), numeric_value = VALUES(numeric_value)";
        this.deleteSql = "DELETE FROM " + table + " WHERE owner = ? AND var_key = ?";
    }

    private static boolean isSqlite(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not detect SQL dialect", e);
        }
    }

    @Override
    public Map<String, String> loadGlobal() {
        return load(GLOBAL_OWNER);
    }

    @Override
    public Map<String, String> loadPlayer(UUID owner) {
        return load(owner.toString());
    }

    private Map<String, String> load(String owner) {
        String sql = "SELECT var_key, value FROM " + table + " WHERE owner = ?";
        Map<String, String> values = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(result.getString(1), result.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load variables for " + owner, e);
        }
        return values;
    }

    @Override
    public void apply(List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (int from = 0; from < changes.size(); from += CHUNK) {
                    write(connection, changes.subList(from, Math.min(changes.size(), from + CHUNK)));
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist " + changes.size() + " variable change(s)", e);
        }
    }

    private void write(Connection connection, List<Change> chunk) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(upsertSql);
             PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            boolean hasUpserts = false;
            boolean hasDeletes = false;
            for (Change change : chunk) {
                if (change.value() == null) {
                    delete.setString(1, change.owner());
                    delete.setString(2, change.key());
                    delete.addBatch();
                    hasDeletes = true;
                    continue;
                }
                upsert.setString(1, change.owner());
                upsert.setString(2, change.key());
                upsert.setString(3, change.value());
                if (change.numeric() == null) {
                    upsert.setNull(4, Types.DOUBLE);
                } else {
                    upsert.setDouble(4, change.numeric());
                }
                upsert.addBatch();
                hasUpserts = true;
            }
            if (hasUpserts) upsert.executeBatch();
            if (hasDeletes) delete.executeBatch();
        }
    }

    @Override
    public int deleteAll(UUID owner) {
        return execute("DELETE FROM " + table + " WHERE owner = ?", s -> s.setString(1, owner.toString()));
    }

    @Override
    public int purgeOrphaned(Collection<String> knownKeys) {
        if (knownKeys.isEmpty()) {
            return execute("DELETE FROM " + table, s -> { });
        }
        String placeholders = String.join(", ", Collections.nCopies(knownKeys.size(), "?"));
        return execute("DELETE FROM " + table + " WHERE var_key NOT IN (" + placeholders + ")", s -> {
            int i = 1;
            for (String key : knownKeys) {
                s.setString(i++, key);
            }
        });
    }

    @Override
    public int backfillNumeric(String key) {
        return execute("UPDATE " + table + " SET numeric_value = value + 0 WHERE var_key = ? AND numeric_value IS NULL",
                s -> s.setString(1, key));
    }

    @Override
    public List<Variable> top(String key, boolean descending, int limit, double minValue) {
        String direction = descending ? "DESC" : "ASC";
        String sql = "SELECT owner, value, numeric_value FROM " + table
                + " WHERE var_key = ? AND numeric_value >= ? AND owner <> ?"
                + " ORDER BY numeric_value " + direction + ", owner " + direction
                + (limit > 0 ? " LIMIT " + limit : "");
        List<Variable> ranked = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setDouble(2, minValue);
            statement.setString(3, GLOBAL_OWNER);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        UUID owner = UUID.fromString(result.getString(1));
                        ranked.add(new Variable(owner, key, result.getString(2), result.getDouble(3)));
                    } catch (IllegalArgumentException ignored) {
                        // malformed owner id, not a player row
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not rank variable '" + key + "'", e);
        }
        return ranked;
    }

    private int execute(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Variable statement failed: " + sql, e);
        }
    }
}