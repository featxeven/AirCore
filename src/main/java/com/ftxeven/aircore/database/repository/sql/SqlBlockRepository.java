package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.BlockRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SqlBlockRepository implements BlockRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlBlockRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "blocks";
    }

    @Override
    public Set<UUID> blockedBy(UUID owner) {
        String sql = "SELECT blocked FROM " + table + " WHERE owner = ?";
        Set<UUID> blocked = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    blocked.add(UUID.fromString(result.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load blocked players for " + owner, e);
        }
        return blocked;
    }

    @Override
    public boolean isBlocked(UUID owner, UUID target) {
        String sql = "SELECT 1 FROM " + table + " WHERE owner = ? AND blocked = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, target.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check block state for " + owner, e);
        }
    }

    @Override
    public int countBlocked(UUID owner) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE owner = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count blocked players for " + owner, e);
        }
    }

    @Override
    public boolean block(UUID owner, UUID target) {
        if (isBlocked(owner, target)) {
            return false;
        }
        String sql = "INSERT INTO " + table + " (owner, blocked, blocked_at) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, target.toString());
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not block " + target + " for " + owner, e);
        }
    }

    @Override
    public boolean unblock(UUID owner, UUID target) {
        String sql = "DELETE FROM " + table + " WHERE owner = ? AND blocked = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, target.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not unblock " + target + " for " + owner, e);
        }
    }

    @Override
    public int unblockAll(UUID owner) {
        String sql = "DELETE FROM " + table + " WHERE owner = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear blocks for " + owner, e);
        }
    }
}