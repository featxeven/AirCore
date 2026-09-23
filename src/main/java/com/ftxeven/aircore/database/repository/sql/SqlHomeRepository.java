package com.ftxeven.aircore.database.repository.sql;

import com.ftxeven.aircore.database.repository.HomeRepository;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SqlHomeRepository implements HomeRepository {

    private final DataSource dataSource;
    private final String table;

    public SqlHomeRepository(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.table = tablePrefix + "homes";
    }

    @Override
    public Optional<Home> find(UUID owner, String name) {
        String sql = "SELECT * FROM " + table + " WHERE owner = ? AND name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapHome(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load home " + name + " for player " + owner, e);
        }
    }

    @Override
    public List<Home> findAll(UUID owner) {
        String sql = "SELECT * FROM " + table + " WHERE owner = ? ORDER BY name ASC";
        List<Home> homes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    homes.add(mapHome(result));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load homes for player " + owner, e);
        }
        return homes;
    }

    @Override
    public int count(UUID owner) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE owner = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count homes for player " + owner, e);
        }
    }

    @Override
    public Home save(Home home) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, icon = ?, favorite = ? "
                            + "WHERE owner = ? AND name = ?")) {
                bindPosition(update, 1, home.position());
                update.setString(7, home.icon());
                update.setBoolean(8, home.favorite());
                update.setString(9, home.owner().toString());
                update.setString(10, home.name());
                if (update.executeUpdate() > 0) {
                    return home;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (owner, name, world, x, y, z, yaw, pitch, icon, favorite, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, home.owner().toString());
                insert.setString(2, home.name());
                bindPosition(insert, 3, home.position());
                insert.setString(9, home.icon());
                insert.setBoolean(10, home.favorite());
                insert.setLong(11, home.createdAt().toEpochMilli());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save home " + home.name() + " for player " + home.owner(), e);
        }
        return home;
    }

    @Override
    public boolean setFavorite(UUID owner, String name, boolean favorite) {
        String sql = "UPDATE " + table + " SET favorite = ? WHERE owner = ? AND name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, favorite);
            statement.setString(2, owner.toString());
            statement.setString(3, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update favorite for home " + name + " (player " + owner + ")", e);
        }
    }

    @Override
    public boolean setIcon(UUID owner, String name, @Nullable String icon) {
        String sql = "UPDATE " + table + " SET icon = ? WHERE owner = ? AND name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, icon);
            statement.setString(2, owner.toString());
            statement.setString(3, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update icon for home " + name + " (player " + owner + ")", e);
        }
    }

    @Override
    public boolean delete(UUID owner, String name) {
        String sql = "DELETE FROM " + table + " WHERE owner = ? AND name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete home " + name + " for player " + owner, e);
        }
    }

    @Override
    public int deleteAll(UUID owner) {
        String sql = "DELETE FROM " + table + " WHERE owner = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner.toString());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete homes for player " + owner, e);
        }
    }

    @Override
    public int deleteAll(Collection<UUID> owners) {
        if (owners.isEmpty()) {
            return 0;
        }
        List<String> ids = owners.stream().map(UUID::toString).toList();
        String placeholders = ids.stream().map(u -> "?").collect(Collectors.joining(", "));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + table + " WHERE owner IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setString(i + 1, ids.get(i));
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not bulk-delete homes", e);
        }
    }

    // Row mapping

    private void bindPosition(PreparedStatement statement, int offset, Position position) throws SQLException {
        statement.setString(offset, position.world());
        statement.setDouble(offset + 1, position.x());
        statement.setDouble(offset + 2, position.y());
        statement.setDouble(offset + 3, position.z());
        statement.setFloat(offset + 4, position.yaw());
        statement.setFloat(offset + 5, position.pitch());
    }

    private Home mapHome(ResultSet rs) throws SQLException {
        return new Home(
                UUID.fromString(rs.getString("owner")),
                rs.getString("name"),
                new Position(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")),
                rs.getString("icon"),
                rs.getBoolean("favorite"),
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }
}