package com.ftxeven.aircore.database;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.player.*;

import java.io.File;
import java.sql.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class DatabaseManager {

    private final AirCore plugin;
    private Connection connection;

    private PlayerRecords playerRecords;
    private PlayerBlocks playerBlocks;
    private PlayerHomes playerHomes;
    private PlayerKits playerKits;
    private PlayerCooldowns playerCooldowns;
    private PlayerInventories playerInventories;
    private final Set<Thread> asyncTasks = Collections.synchronizedSet(new HashSet<>());

    public DatabaseManager(AirCore plugin) {
        this.plugin = plugin;
    }

    public void init() throws SQLException {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataFolder.getAbsolutePath());
        }

        File dbFile = new File(dataFolder, "database.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        this.connection = DriverManager.getConnection(url);
        this.connection.setAutoCommit(true);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }

        createTables();

        this.playerRecords = new PlayerRecords(plugin, connection);
        this.playerBlocks = new PlayerBlocks(plugin, connection);
        this.playerHomes = new PlayerHomes(plugin, connection);
        this.playerKits = new PlayerKits(plugin, connection);
        this.playerCooldowns = new PlayerCooldowns(plugin, connection);
        this.playerInventories = new PlayerInventories(plugin, connection);

        plugin.getNameCache().putAll(playerRecords.loadAllNames());
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_records (
                join_index INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """);
            addColumn(stmt, "player_records", "balance", "REAL NOT NULL DEFAULT 0");
            addColumn(stmt, "player_records", "walk_speed", "REAL NOT NULL DEFAULT 1.0");
            addColumn(stmt, "player_records", "fly_speed", "REAL NOT NULL DEFAULT 1.0");
            addColumn(stmt, "player_records", "chat_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumn(stmt, "player_records", "mentions_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumn(stmt, "player_records", "pm_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumn(stmt, "player_records", "socialspy_enabled", "INTEGER NOT NULL DEFAULT 0");
            addColumn(stmt, "player_records", "pay_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumn(stmt, "player_records", "teleport_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumn(stmt, "player_records", "god_enabled", "INTEGER NOT NULL DEFAULT 0");
            addColumn(stmt, "player_records", "fly_enabled", "INTEGER NOT NULL DEFAULT 0");
            addColumn(stmt, "player_records", "world", "TEXT");
            addColumn(stmt, "player_records", "x", "REAL");
            addColumn(stmt, "player_records", "y", "REAL");
            addColumn(stmt, "player_records", "z", "REAL");
            addColumn(stmt, "player_records", "yaw", "REAL");
            addColumn(stmt, "player_records", "pitch", "REAL");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_inventories (uuid TEXT PRIMARY KEY);");
            addColumn(stmt, "player_inventories", "contents", "BLOB");
            addColumn(stmt, "player_inventories", "armor", "BLOB");
            addColumn(stmt, "player_inventories", "offhand", "BLOB");
            addColumn(stmt, "player_inventories", "enderchest", "BLOB");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_cooldowns (
                uuid TEXT NOT NULL,
                command TEXT NOT NULL,
                expiry INTEGER NOT NULL,
                PRIMARY KEY (uuid, command)
            );
        """);

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_blocks (
                uuid TEXT NOT NULL,
                blocked_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (uuid, blocked_uuid)
            );
        """);

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_homes (
                uuid TEXT NOT NULL,
                name TEXT NOT NULL,
                world TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                yaw REAL NOT NULL,
                pitch REAL NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (uuid, name)
            );
        """);

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_kits (
                uuid TEXT NOT NULL,
                kit TEXT NOT NULL,
                PRIMARY KEY (uuid, kit)
            );
        """);
            addColumn(stmt, "player_kits", "last_claim", "INTEGER NOT NULL DEFAULT 0");
            addColumn(stmt, "player_kits", "one_time_claimed", "INTEGER NOT NULL DEFAULT 0");
            addColumn(stmt, "player_kits", "last_cooldown", "INTEGER NOT NULL DEFAULT 0");
        }
    }

    private void addColumn(Statement stmt, String table, String column, String type) throws SQLException {
        if (!columnExists(table, column)) {
            plugin.getLogger().warning("Adding missing column '" + column + "' to table '" + table + "'");
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    public void close() {
        synchronized (asyncTasks) {
            for (Thread t : asyncTasks) {
                try {
                    t.join(2000);
                } catch (InterruptedException ignored) {}
            }
            asyncTasks.clear();
        }

        if (connection != null) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    @FunctionalInterface
    public interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    public void executeAsync(String sql, SQLConsumer<PreparedStatement> binder) {
        Thread task = new Thread(() -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                binder.accept(ps);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("SQL failed: " + e.getMessage());
            } finally {
                asyncTasks.remove(Thread.currentThread());
            }
        }, "AirCore-DBTask");

        asyncTasks.add(task);
        plugin.scheduler().runAsync(task);
    }

    public PlayerRecords records() { return playerRecords; }
    public PlayerBlocks blocks() { return playerBlocks; }
    public PlayerHomes homes() { return playerHomes; }
    public PlayerKits kits() { return playerKits; }
    public PlayerCooldowns cooldowns() { return playerCooldowns; }
    public PlayerInventories inventories() { return playerInventories; }
}
