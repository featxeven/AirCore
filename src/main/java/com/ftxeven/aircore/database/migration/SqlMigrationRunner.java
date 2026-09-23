package com.ftxeven.aircore.database.migration;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class SqlMigrationRunner implements MigrationRunner {

    private final JavaPlugin plugin;
    private final DataSource dataSource;
    private final String tablePrefix;
    private final String versionTable;

    public SqlMigrationRunner(JavaPlugin plugin, DataSource dataSource, String tablePrefix) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix;
        this.versionTable = tablePrefix + "schema_version";
    }

    @Override
    public int currentVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ensureTrackingTable(connection);
            return currentVersion(connection);
        }
    }

    @Override
    public void migrate(List<Migration> available) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ensureTrackingTable(connection);
            int current = currentVersion(connection);

            for (Migration migration : available) {
                if (migration.version() <= current) {
                    continue;
                }
                apply(connection, migration);
                plugin.getLogger().info("Applied migration V" + migration.version() + " (" + migration.description() + ")");
            }
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        String content = migration.content().replace("{prefix}", tablePrefix);

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String sql : content.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            statement.executeUpdate("INSERT INTO " + versionTable + " (version, description) VALUES ("
                    + migration.version() + ", '" + migration.description().replace("'", "''") + "')");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) FROM " + versionTable)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void ensureTrackingTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        version INT PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(versionTable));
        }
    }
}