package com.ftxeven.aircore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class StorageConfig extends BaseConfig {

    private volatile Database database;

    public StorageConfig(JavaPlugin plugin) {
        super(plugin, "storage.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        database = readDatabase(yaml.getConfigurationSection("database"));
    }

    public Database database() { return database; }

    private Database readDatabase(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Database(
                enumOr(sec, "driver", Driver.class, Driver.SQLITE),
                getString(sec, "table-prefix", "ac_"),
                readSqlite(sec.getConfigurationSection("sqlite")),
                readMysql(sec.getConfigurationSection("mysql")),
                readMongodb(sec.getConfigurationSection("mongodb"))
        );
    }

    private Sqlite readSqlite(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Sqlite(getString(sec, "file", "data/database.db"));
    }

    private Mysql readMysql(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Mysql(
                getString(sec, "host", "localhost"),
                getInt(sec, "port", 3306),
                getString(sec, "database", "aircore"),
                getString(sec, "user", "root"),
                getString(sec, "password", ""),
                getBoolean(sec, "ssl", false),
                getBoolean(sec, "auto-reconnect", true),
                readMysqlPool(sec.getConfigurationSection("pool"))
        );
    }

    private MysqlPool readMysqlPool(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new MysqlPool(
                getInt(sec, "size", 5),
                getString(sec, "name", "AirCore-Pool"),
                getInt(sec, "connection-timeout", 30000),
                getInt(sec, "idle-timeout", 600000),
                getInt(sec, "max-lifetime", 1800000)
        );
    }

    private Mongodb readMongodb(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Mongodb(
                getString(sec, "uri", ""),
                getString(sec, "host", "localhost"),
                getInt(sec, "port", 27017),
                getString(sec, "database", "aircore"),
                getString(sec, "user", ""),
                getString(sec, "password", ""),
                getString(sec, "auth-source", "admin"),
                getBoolean(sec, "ssl", false),
                readMongodbPool(sec.getConfigurationSection("pool"))
        );
    }

    private MongodbPool readMongodbPool(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new MongodbPool(
                getInt(sec, "min-size", 0),
                getInt(sec, "max-size", 5),
                getInt(sec, "connect-timeout", 10000),
                getInt(sec, "server-selection-timeout", 5000),
                getInt(sec, "idle-timeout", 600000),
                getInt(sec, "max-lifetime", 1800000)
        );
    }

    // Section types

    public enum Driver { SQLITE, MYSQL, MARIADB, MONGODB }

    public record Sqlite(String file) {}

    public record MysqlPool(int size, String name, int connectionTimeout, int idleTimeout, int maxLifetime) {}

    public record Mysql(String host, int port, String database, String user, String password, boolean ssl, boolean autoReconnect, MysqlPool pool) {}

    public record MongodbPool(int minSize, int maxSize, int connectTimeout, int serverSelectionTimeout, int idleTimeout, int maxLifetime) {}

    public record Mongodb(String uri, String host, int port, String database, String user, String password, String authSource, boolean ssl, MongodbPool pool) {}

    public record Database(Driver driver, String tablePrefix, Sqlite sqlite, Mysql mysql, Mongodb mongodb) {}
}