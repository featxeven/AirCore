package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.CooldownRepository;
import com.ftxeven.aircore.model.Cooldown;
import com.ftxeven.aircore.model.CooldownScope;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlCooldownRepository implements CooldownRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlCooldownRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "cooldowns";
    }

    @Override
    public Optional<Instant> find(UUID owner, CooldownScope scope, String key, String arg, Instant now) {
        String sql = "SELECT expires_at FROM " + table + " WHERE owner = ? AND scope = ? AND cooldown_key = ? AND arg = ? AND expires_at > ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, scope.name());
            statement.setString(3, key);
            statement.setString(4, arg);
            statement.setLong(5, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(Instant.ofEpochMilli(result.getLong(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read cooldown " + key + " for player " + owner, e);
        }
    }

    @Override
    public List<Cooldown> findAllActive(UUID owner, Instant now) {
        String sql = "SELECT scope, cooldown_key, arg, expires_at FROM " + table + " WHERE owner = ? AND expires_at > ?";
        List<Cooldown> cooldowns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setLong(2, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    cooldowns.add(new Cooldown(owner, CooldownScope.valueOf(result.getString(1)),
                            result.getString(2), result.getString(3), Instant.ofEpochMilli(result.getLong(4))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load active cooldowns for player " + owner, e);
        }
        return cooldowns;
    }

    @Override
    public void set(UUID owner, CooldownScope scope, String key, String arg, Instant expiresAt) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET expires_at = ? WHERE owner = ? AND scope = ? AND cooldown_key = ? AND arg = ?")) {
                update.setLong(1, expiresAt.toEpochMilli());
                update.setString(2, owner.toString());
                update.setString(3, scope.name());
                update.setString(4, key);
                update.setString(5, arg);
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (owner, scope, cooldown_key, arg, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, owner.toString());
                insert.setString(2, scope.name());
                insert.setString(3, key);
                insert.setString(4, arg);
                insert.setLong(5, expiresAt.toEpochMilli());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not set cooldown " + key + " for player " + owner, e);
        }
    }

    @Override
    public void clear(UUID owner, CooldownScope scope, String key, String arg) {
        String sql = "DELETE FROM " + table + " WHERE owner = ? AND scope = ? AND cooldown_key = ? AND arg = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, scope.name());
            statement.setString(3, key);
            statement.setString(4, arg);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear cooldown " + key + " for player " + owner, e);
        }
    }

    @Override
    public void clearAll(UUID owner, CooldownScope scope) {
        String sql = "DELETE FROM " + table + " WHERE owner = ? AND scope = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, scope.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear " + scope + " cooldowns for player " + owner, e);
        }
    }

    @Override
    public int clearAllForKey(CooldownScope scope, String key) {
        String sql = "DELETE FROM " + table + " WHERE scope = ? AND cooldown_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scope.name());
            statement.setString(2, key);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear " + scope + " cooldowns for key " + key, e);
        }
    }

    @Override
    public int purgeExpired(Instant before) {
        String sql = "DELETE FROM " + table + " WHERE expires_at <= ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, before.toEpochMilli());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not purge expired cooldowns", e);
        }
    }
}