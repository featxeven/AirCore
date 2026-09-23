package com.ftxeven.aircore.database.id;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SqlSequence {

    private SqlSequence() {
    }

    public static int next(Connection connection, String table, String key) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET value = value + 1 WHERE sequence_key = ?")) {
            update.setString(1, key);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + table + " (sequence_key, value) VALUES (?, 1)")) {
                    insert.setString(1, key);
                    insert.executeUpdate();
                }
                return 1;
            }
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT value FROM " + table + " WHERE sequence_key = ?")) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}