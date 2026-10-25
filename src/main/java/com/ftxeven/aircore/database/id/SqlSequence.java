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

    // never lowers the sequence, only makes sure it is at least floor
    public static void raiseTo(Connection connection, String table, String key, int floor) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET value = ? WHERE sequence_key = ? AND value < ?")) {
            update.setInt(1, floor);
            update.setString(2, key);
            update.setInt(3, floor);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE sequence_key = ?")) {
            exists.setString(1, key);
            try (ResultSet result = exists.executeQuery()) {
                if (result.next()) {
                    return; // already at or above the floor
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + " (sequence_key, value) VALUES (?, ?)")) {
            insert.setString(1, key);
            insert.setInt(2, floor);
            insert.executeUpdate();
        }
    }
}