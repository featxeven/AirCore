package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.model.NamedLocation;

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

public final class SqlLocationRepository implements LocationRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlLocationRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "locations";
    }

    @Override
    public Optional<NamedLocation> find(Category category, String key) {
        String sql = "SELECT * FROM " + table + " WHERE category = ? AND loc_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.name());
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapLocation(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load " + category + " location " + key, e);
        }
    }

    @Override
    public List<NamedLocation> findAll(Category category) {
        String sql = "SELECT * FROM " + table + " WHERE category = ? ORDER BY loc_key ASC";
        List<NamedLocation> locations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    locations.add(mapLocation(result));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load " + category + " locations", e);
        }
        return locations;
    }

    @Override
    public void save(Category category, NamedLocation location) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? WHERE category = ? AND loc_key = ?")) {
                bindPosition(update, 1, location.position());
                update.setString(7, category.name());
                update.setString(8, location.key());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (category, loc_key, world, x, y, z, yaw, pitch, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, category.name());
                insert.setString(2, location.key());
                bindPosition(insert, 3, location.position());
                insert.setLong(9, location.createdAt().toEpochMilli());
                insert.setString(10, location.createdBy() != null ? location.createdBy().toString() : null);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save " + category + " location " + location.key(), e);
        }
    }

    @Override
    public boolean delete(Category category, String key) {
        String sql = "DELETE FROM " + table + " WHERE category = ? AND loc_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.name());
            statement.setString(2, key);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete " + category + " location " + key, e);
        }
    }

    private void bindPosition(PreparedStatement statement, int offset, Position position) throws SQLException {
        statement.setString(offset, position.world());
        statement.setDouble(offset + 1, position.x());
        statement.setDouble(offset + 2, position.y());
        statement.setDouble(offset + 3, position.z());
        statement.setFloat(offset + 4, position.yaw());
        statement.setFloat(offset + 5, position.pitch());
    }

    private NamedLocation mapLocation(ResultSet rs) throws SQLException {
        String createdBy = rs.getString("created_by");
        return new NamedLocation(
                rs.getString("loc_key"),
                new Position(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                createdBy != null ? UUID.fromString(createdBy) : null);
    }
}