package com.ftxeven.aircore.database;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.database.dao.*;

import java.io.File;
import java.sql.*;

public final class DatabaseManager {

    private final AirCore plugin;
    private Connection connection;

    private PlayerRecords playerRecords;
    private PlayerBlocks playerBlocks;
    private PlayerHomes playerHomes;
    private PlayerKits playerKits;
    private PlayerCooldowns playerCooldowns;
    private PlayerInventories playerInventories;

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
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_records (
                    join_index INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    balance REAL NOT NULL DEFAULT 0,
                    walk_speed REAL NOT NULL DEFAULT 1.0,
                    fly_speed REAL NOT NULL DEFAULT 1.0,
                    chat_enabled INTEGER NOT NULL DEFAULT 1,
                    mentions_enabled INTEGER NOT NULL DEFAULT 1,
                    pm_enabled INTEGER NOT NULL DEFAULT 1,
                    socialspy_enabled INTEGER NOT NULL DEFAULT 0,
                    pay_enabled INTEGER NOT NULL DEFAULT 1,
                    teleport_enabled INTEGER NOT NULL DEFAULT 1,
                    god_enabled INTEGER NOT NULL DEFAULT 0,
                    fly_enabled INTEGER NOT NULL DEFAULT 0,
                    world TEXT,
                    x REAL,
                    y REAL,
                    z REAL,
                    yaw REAL,
                    pitch REAL
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_inventories (
                    uuid TEXT PRIMARY KEY,
                    contents BLOB,
                    armor BLOB,
                    offhand BLOB,
                    enderchest BLOB
                );
            """);

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
                    last_claim INTEGER NOT NULL DEFAULT 0,
                    one_time_claimed INTEGER NOT NULL DEFAULT 0,
                    last_cooldown INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, kit)
                );
            """);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    @FunctionalInterface
    public interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    public synchronized void executeAsync(String sql, SQLConsumer<PreparedStatement> binder) {
        plugin.scheduler().runAsync(() -> {
            if (isClosed()) return;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                binder.accept(ps);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!isClosed()) {
                    plugin.getLogger().warning("SQL failed: " + e.getMessage());
                }
            }
        });
    }

    public boolean isClosed() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    public PlayerRecords records() { return playerRecords; }
    public PlayerBlocks blocks() { return playerBlocks; }
    public PlayerHomes homes() { return playerHomes; }
    public PlayerKits kits() { return playerKits; }
    public PlayerCooldowns cooldowns() { return playerCooldowns; }
    public PlayerInventories inventories() { return playerInventories; }
}