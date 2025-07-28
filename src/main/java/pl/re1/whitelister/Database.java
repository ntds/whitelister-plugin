package pl.re1.whitelister;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import javax.xml.crypto.Data;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Database {
    private static Connection connection;

    public Database(String url) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(url);

            if (connection.isClosed()) {
                return;
            }

            Whitelister.instance.getLogger().info("Database connection successful!");
        } catch (SQLException err) {
            String error = String.format("Connection error with database, err: %s", err.getMessage());
            Whitelister.instance.getLogger().severe(error);
        } catch (ClassNotFoundException err) {
            String error = String.format("Connection error with runtime, err: %s", err.getMessage());
            Whitelister.instance.getLogger().severe(error);
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                Whitelister.instance.getLogger().info("Database connection closed successfully.");
            } catch (SQLException e) {
                Whitelister.instance.getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        } else {
            Whitelister.instance.getLogger().info("No database connection to close.");
        }
    }

    private static <T> void preparedStatementSet(PreparedStatement prepared_statement, int index, T value) throws SQLException {
        if (prepared_statement.isClosed()) {
            throw new SQLException("Prepared statement cannot be closed before setting values.");
        }

        if (value instanceof String) {
            prepared_statement.setString(index, (String) value);
        } else if (value instanceof Integer) {
            prepared_statement.setInt(index, (Integer) value);
        } else if (value instanceof Double) {
            prepared_statement.setDouble(index, (Double) value);
        } else if (value instanceof Boolean) {
            prepared_statement.setBoolean(index, (Boolean) value);
        } else if (value instanceof java.sql.Date) {
            prepared_statement.setDate(index, (java.sql.Date) value);
        } else if (value == null) {
            prepared_statement.setObject(index, null);
        } else {
            prepared_statement.setObject(index, value);
        }
    }

    private static List<Map<String, Object>> getValuesFromResult(ResultSet result_set) throws SQLException {
        if (result_set.isClosed()) {
            throw new SQLException("Result set cannot be closed before getting values.");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        ResultSetMetaData meta_data = result_set.getMetaData();
        int columnCount = meta_data.getColumnCount();

        while (result_set.next()) {
            Map<String, Object> row = new HashMap<>();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta_data.getColumnLabel(i);
                Object value = result_set.getObject(i);
                row.put(columnName, value);
            }

            results.add(row);
        }

        return results;
    }

    private static <T> void setValues(String sql, T[] values) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connection to database not open.");
        }

        PreparedStatement prepared_statement = connection.prepareStatement(sql);

        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            int index = i + 1;

            preparedStatementSet(prepared_statement, index, value);
        }

        int row_count = prepared_statement.executeUpdate();

        String print_row = String.format("Successfully updated %d rows.", row_count);
        Whitelister.instance.getLogger().info(print_row);

        prepared_statement.close();
    }

    private static <T> List<Map<String, Object>> getValues(String sql, T[] values) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connection to database not open.");
        }

        PreparedStatement prepared_statement = connection.prepareStatement(sql);

        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            int index = i + 1;

            preparedStatementSet(prepared_statement, index, value);
        }

        prepared_statement.executeQuery();

        ResultSet result_set = prepared_statement.getResultSet();

        List<Map<String, Object>> res = getValuesFromResult(result_set);

        result_set.close();
        prepared_statement.close();

        return res;
    }

    private static void executeSQL(String sql) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connection to database not open.");
        }

        PreparedStatement prepared_statement = connection.prepareStatement(sql);
        prepared_statement.execute();

        prepared_statement.close();
    }

    public void createUserTable() {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "CREATE TABLE IF NOT EXISTS " + table_name + " (" +
                     "uuid VARCHAR(36) PRIMARY KEY, " +
                     "name VARCHAR(16) NOT NULL, " +
                     "whitelisted BOOLEAN DEFAULT FALSE" +
                     ")";

        try {
            executeSQL(sql);

            Whitelister.instance.getLogger().info("User table created successfully.");
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error creating user table: " + e.getMessage());
        }
    }

    public void addUser(UUID user_uuid, String user_name, boolean whitelisted) {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "INSERT INTO " + table_name + " (uuid, name, whitelisted) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = VALUES(name), whitelisted = VALUES(whitelisted)";

        try {
            setValues(sql, new Object[]{ user_uuid.toString(), user_name, whitelisted });
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error adding user error: " + e.getMessage());
        }
    }

    public void setWhitelisted(UUID user_uuid, boolean whitelisted) {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "UPDATE " + table_name + " SET whitelisted = ? WHERE uuid = ?";

        try {
            setValues(sql, new Object[]{whitelisted, user_uuid.toString()});
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error updating whitelist status error: " + e.getMessage());
        }
    }

    public boolean userExists(UUID user_uuid) {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "SELECT whitelisted FROM " + table_name + " WHERE uuid = ?";

        try {
            List<Map<String, Object>> results = getValues(sql, new Object[]{ user_uuid.toString() });

            return !results.isEmpty();
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error retrieving existing user error: " + e.getMessage());
            return false;
        }
    }

    public boolean isWhitelisted(UUID user_uuid) {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "SELECT whitelisted FROM " + table_name + " WHERE uuid = ?";

        try {
            List<Map<String, Object>> results = getValues(sql, new Object[]{ user_uuid.toString() });
            if (results.isEmpty()) {
                return false;
            }
            return (Boolean) results.get(0).get("whitelisted");
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error retrieving whitelist status error: " + e.getMessage());
            return false;
        }
    }

    public @Nullable Player[] getAllowedPlayers() {
        String table_name = Config.getConfig().getString("mysql.table");

        String sql = "SELECT * FROM " + table_name + " WHERE whitelisted = true";

        try {
            List<Map<String, Object>> results = getValues(sql, new Object[]{});

            if (results.isEmpty()) {
                return null;
            }

            List<Player> players = new ArrayList<>();

            for (Map<String, Object> val : results) {
                UUID uuid = UUID.fromString(val.get("uuid").toString());
                Player player = Bukkit.getPlayer(uuid);

                players.add(player);
            }

            return players.toArray(new Player[0]);
        } catch (SQLException e) {
            Whitelister.instance.getLogger().severe("Error retrieving whitelist allowed players error: " + e.getMessage());

            return null;
        }
    }
}