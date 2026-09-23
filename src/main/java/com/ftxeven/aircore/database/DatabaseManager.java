package com.ftxeven.aircore.database;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.StorageConfig;
import com.ftxeven.aircore.database.migration.MigrationLoader;
import com.ftxeven.aircore.database.migration.MigrationRunner;
import com.ftxeven.aircore.database.migration.MongoMigrationRunner;
import com.ftxeven.aircore.database.migration.SqlMigrationRunner;
import com.ftxeven.aircore.database.repository.*;
import com.ftxeven.aircore.database.repository.mongo.*;
import com.ftxeven.aircore.database.repository.sql.*;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {

    private final JavaPlugin plugin;
    private final ConfigManager configs;

    private HikariDataSource dataSource;
    private MongoClient mongoClient;

    private PlayerRepository players;
    private PlayerInventoryRepository inventories;
    private HomeRepository homes;
    private CooldownRepository cooldowns;
    private BlockRepository blocks;
    private VariableRepository variables;
    private LocationRepository locations;
    private KitRepository kits;
    private PersistentBossbarRepository persistentBossbar;

    public DatabaseManager(JavaPlugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean connect() {
        try {
            StorageConfig.Database db = configs.storage().database();
            String prefix = db.tablePrefix();

            if (db.driver() == StorageConfig.Driver.MONGODB) {
                MongoDatabase database = connectMongo(db.mongodb());
                runMigrations(new MongoMigrationRunner(plugin, database, prefix), StorageConfig.Driver.MONGODB);

                players = new MongoPlayerRepository(database, prefix);
                inventories = new MongoPlayerInventoryRepository(database, prefix);
                homes = new MongoHomeRepository(database, prefix);
                cooldowns = new MongoCooldownRepository(database, prefix);
                blocks = new MongoBlockRepository(database, prefix);
                variables = new MongoVariableRepository(database, prefix);
                locations = new MongoLocationRepository(database, prefix);
                kits = new MongoKitRepository(database, prefix);
                persistentBossbar = new MongoPersistentBossbarRepository(database, prefix);
            } else {
                connectSql(db);
                runMigrations(new SqlMigrationRunner(plugin, dataSource, prefix), db.driver());

                players = new SqlPlayerRepository(dataSource, prefix);
                inventories = new SqlPlayerInventoryRepository(dataSource, prefix);
                homes = new SqlHomeRepository(dataSource, prefix);
                cooldowns = new SqlCooldownRepository(dataSource, prefix);
                blocks = new SqlBlockRepository(dataSource, prefix);
                variables = new SqlVariableRepository(dataSource, prefix);
                locations = new SqlLocationRepository(dataSource, prefix);
                kits = new SqlKitRepository(dataSource, prefix);
                persistentBossbar = new SqlPersistentBossbarRepository(dataSource, prefix);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to the database: " + e.getMessage());
            close();
            return false;
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    public PlayerRepository players() { return players; }
    public PlayerInventoryRepository inventories() { return inventories; }
    public HomeRepository homes() { return homes; }
    public CooldownRepository cooldowns() { return cooldowns; }
    public BlockRepository blocks() { return blocks; }
    public VariableRepository variables() { return variables; }
    public LocationRepository locations() { return locations; }
    public KitRepository kits() { return kits; }
    public PersistentBossbarRepository persistentBossbar() { return persistentBossbar; }

    private void runMigrations(MigrationRunner runner, StorageConfig.Driver driver) throws Exception {
        List<MigrationRunner.Migration> migrations = new MigrationLoader(plugin).load(MigrationLoader.folderFor(driver));
        runner.migrate(migrations);
    }

    private void connectSql(StorageConfig.Database db) {
        HikariConfig hikari = new HikariConfig();
        if (db.driver() == StorageConfig.Driver.SQLITE) {
            File file = new File(plugin.getDataFolder(), db.sqlite().file());
            file.getParentFile().mkdirs();
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
        } else {
            StorageConfig.Mysql mysql = db.mysql();
            hikari.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                    + "?useSSL=" + mysql.ssl() + "&autoReconnect=" + mysql.autoReconnect());
            hikari.setUsername(mysql.user());
            hikari.setPassword(mysql.password());
            hikari.setMaximumPoolSize(mysql.pool().size());
            hikari.setPoolName(mysql.pool().name());
            hikari.setConnectionTimeout(mysql.pool().connectionTimeout());
            hikari.setIdleTimeout(mysql.pool().idleTimeout());
            hikari.setMaxLifetime(mysql.pool().maxLifetime());
        }
        dataSource = new HikariDataSource(hikari);
    }

    private MongoDatabase connectMongo(StorageConfig.Mongodb mongo) {
        MongoClientSettings.Builder settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongo.uri().isBlank() ? plainConnectionString(mongo) : mongo.uri()));
        settings.applyToConnectionPoolSettings(builder -> builder
                .minSize(mongo.pool().minSize())
                .maxSize(mongo.pool().maxSize())
                .maxConnectionIdleTime(mongo.pool().idleTimeout(), TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(mongo.pool().maxLifetime(), TimeUnit.MILLISECONDS));
        settings.applyToClusterSettings(builder -> builder
                .serverSelectionTimeout(mongo.pool().serverSelectionTimeout(), TimeUnit.MILLISECONDS));
        settings.applyToSocketSettings(builder -> builder
                .connectTimeout(mongo.pool().connectTimeout(), TimeUnit.MILLISECONDS));
        mongoClient = MongoClients.create(settings.build());
        return mongoClient.getDatabase(mongo.database());
    }

    private String plainConnectionString(StorageConfig.Mongodb mongo) {
        String credentials = mongo.user().isBlank() ? "" : mongo.user() + ":" + encode(mongo.password()) + "@";
        return "mongodb://" + credentials + mongo.host() + ":" + mongo.port() + "/" + mongo.database()
                + "?authSource=" + mongo.authSource() + "&ssl=" + mongo.ssl();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}