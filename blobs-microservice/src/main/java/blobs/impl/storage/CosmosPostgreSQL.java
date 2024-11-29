package blobs.impl.storage;

import blobs.api.Result;
import blobs.impl.data.User;

import java.sql.*;

import static blobs.api.Result.ErrorCode.*;
import static blobs.impl.rest.BlobsMicroService.POSTGRES_URL;

public class CosmosPostgreSQL implements Database {

    private static CosmosPostgreSQL instance;

    synchronized public static CosmosPostgreSQL getInstance() {
        if (instance == null)
            instance = new CosmosPostgreSQL();

        return instance;
    }

    private static Connection getConnection() throws SQLException {
        try {
            Connection conn = null;
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(POSTGRES_URL);
            return conn;
        } catch (Exception e) {
            System.out.println("Failed to create JDBC db connection " + e + e.getMessage());
        }
        return null;
    }

    private <T> Result<?> getById(String id, Class<T> clazz) throws SQLException {
        if(clazz.equals(User.class)) {
            try (Connection connection = getConnection();
                 PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM users WHERE userId = ?;")) {
                 readStatement.setString(1, id);
                ResultSet resultSet = readStatement.executeQuery();

                if (!resultSet.next())
                    return Result.error(NOT_FOUND);

                User u = new User();
                u.setUserId(resultSet.getString("userId"));
                u.setPwd(resultSet.getString("pwd"));
                u.setEmail(resultSet.getString("email"));
                u.setDisplayName(resultSet.getString("displayName"));
                return Result.ok(u);
            }
        }
        return null;
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        try {
            return (Result<T>) getById(id, clazz);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
