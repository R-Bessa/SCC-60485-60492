package tukano.impl.storage.db.azure;


import com.azure.cosmos.CosmosContainer;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.Session;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.storage.db.Database;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.api.Result.ErrorCode.NOT_IMPLEMENTED;
import static tukano.impl.storage.db.DB.*;

public class CosmosPostgreSQL implements Database {

    private static CosmosPostgreSQL instance;

    private static final String DB_USERNAME = "db.username";
    private static final String DB_PASSWORD = "db.password";
    private static final String DB_URL = "db.url";
    private static final String DB_DRIVER_CLASS = "driver.class.name";

    private static DataSource datasource;
    private static Connection connection;

    synchronized public static CosmosPostgreSQL getInstance() {
        if (instance == null) {
            instance = new CosmosPostgreSQL();
        }
        return instance;
    }

    public synchronized void init() {
        try {
            datasource = getDataSource();
            connection = datasource.getConnection();
            Scanner scanner = new Scanner(CosmosPostgreSQL.class.getClassLoader().getResourceAsStream("schema.sql"));
            Statement statement = connection.createStatement();
            while (scanner.hasNextLine()) {
                statement.execute(scanner.nextLine());
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static DataSource getDataSource() {
        try {
            Properties properties = new Properties();
            //properties.load(new FileInputStream("src/main/resources/application.properties"));

            // Use ClassLoader to load the properties file
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties");

            if (inputStream == null) {
                throw new FileNotFoundException("application.properties file not found in the classpath");
            }

            properties.load(inputStream);

            HikariDataSource ds = new HikariDataSource();
            ds.setDriverClassName(properties.getProperty(DB_DRIVER_CLASS ));
            ds.setJdbcUrl(properties.getProperty(DB_URL));
            ds.setUsername(properties.getProperty(DB_USERNAME));
            ds.setPassword(properties.getProperty(DB_PASSWORD));
            ds.setMinimumIdle(100);
            ds.setMaximumPoolSize(1000000000);
            ds.setAutoCommit(true);
            ds.setLoginTimeout(3);

            return ds;

        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T> Result<T> insertOne(T obj) throws SQLException {
        if (obj instanceof User u) {
            String insertUser = format("INSERT INTO users VALUES ('%s', '%s', '%s', '%s');", u.getUserId(), u.getPwd(), u.getEmail(), u.getDisplayName());
            PreparedStatement insertUserStatement = connection.prepareStatement(insertUser);
            insertUserStatement.executeUpdate();
        }
        else if (obj instanceof Likes l) {
            PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO likes VALUES (?, ?, ?, ?)");
            insertStatement.setString(1, l.getId());
            insertStatement.setString(2, l.getUserId());
            insertStatement.setString(3, l.getShortId());
            insertStatement.setString(4, l.getOwnerId());
            insertStatement.executeUpdate();
        }
        else if (obj instanceof Following f) {
            PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO following VALUES (?, ?, ?)");
            insertStatement.setString(1, f.getId());
            insertStatement.setString(2, f.getFollower());
            insertStatement.setString(3, f.getFollowee());
            insertStatement.executeUpdate();
        }
        else {
            Short s = (Short)obj;
            PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO shorts VALUES (?, ?, ?, ?, ?)");
            insertStatement.setString(1, s.getShortId());
            insertStatement.setString(2, s.getOwnerId());
            insertStatement.setString(3, s.getBlobUrl());
            insertStatement.setLong(4, s.getTimestamp());
            insertStatement.setInt(5, s.getTotalLikes());
        }
        return Result.ok(obj);
    }

    private <T> Result<T> update(T obj) throws SQLException {
        if (obj instanceof User u) {
            String updateUser = format("UPDATE users SET pwd = '%s', email= '%s', displayName = '%s' WHERE userId = '%s'" , u.getPwd(), u.getEmail(), u.getDisplayName(), u.getUserId());
            PreparedStatement insertUserStatement = connection.prepareStatement(updateUser);
            insertUserStatement.executeUpdate();
        }
        else if (obj instanceof Likes l) {
            String updateLikes = format("UPDATE likes SET userid = '%s', shortId = '%s', ownerId = '%s' WHERE id='%s'", l.getUserId(), l.getShortId(), l.getOwnerId(), l.getId());
            PreparedStatement insertLikesStatement = connection.prepareStatement(updateLikes);
            insertLikesStatement.executeUpdate();
        }
        else if (obj instanceof Following f) {
            String updateFollowing = format("UPDATE following SET follower = '%s', followee = '%s' WHERE id = '%s'", f.getFollower(), f.getFollowee(), f.getId());
            PreparedStatement insertFollowingStatement = connection.prepareStatement(updateFollowing);
            insertFollowingStatement.executeUpdate();
        }
        else {
            Short s = (Short)obj;
            String updateShort = format("UPDATE shorts SET ownerId = '%s', blobUrl = '%s', timestamp = '%s', totalLikes = '%s' WHERE shortId = '%s'", s.getOwnerId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getShortId());
            PreparedStatement insertShortStatement = connection.prepareStatement(updateShort);
            insertShortStatement.executeUpdate();
        }
        return Result.ok(obj);
    }

    private <T> Result<T> delete(T obj) throws SQLException {
        if (obj instanceof User u) {
            String deleteUser = format("DELETE FROM users WHERE userId = '%s'", u.getUserId());
            PreparedStatement insertUserStatement = connection.prepareStatement(deleteUser);
            insertUserStatement.executeUpdate();
        }
        else if (obj instanceof Likes l) {
            String deleteLikes = format("DELETE FROM likes WHERE id = '%s'", l.getId());
            PreparedStatement insertLikesStatement = connection.prepareStatement(deleteLikes);
            insertLikesStatement.executeUpdate();
        }
        else if (obj instanceof Following f) {
            String deleteFollowing = format("DELETE FROM following WHERE id = '%s'", f.getId());
            PreparedStatement insertFollowingStatement = connection.prepareStatement(deleteFollowing);
            insertFollowingStatement.executeUpdate();
        }
        else {
            Short s = (Short)obj;
            String deleteShort = format("DELETE FROM shorts WHERE shortId = '%s'", s.getShortId());
            PreparedStatement insertShortStatement = connection.prepareStatement(deleteShort);
            insertShortStatement.executeUpdate();
        }
        return Result.ok(obj);
    }

    private <T> Result<?> getById(String id, Class<T> clazz) throws SQLException {
        if(clazz.equals(User.class)) {
            String getUser = format("SELECT * FROM users WHERE userId = '%s';", id);
            PreparedStatement readStatement = connection.prepareStatement(getUser);
            ResultSet resultSet = readStatement.executeQuery();
            if (!resultSet.next()) {
                return Result.error(NOT_FOUND);
            }
            User u = new User();
            u.setUserId(resultSet.getString("userId"));
            u.setPwd(resultSet.getString("pwd"));
            u.setEmail(resultSet.getString("email"));
            u.setDisplayName(resultSet.getString("displayName"));
            return Result.ok(u);
        }
        else if (clazz.equals(Likes.class)) {
            String getLikes = format("SELECT * FROM likes WHERE id = '%s';", id);
            PreparedStatement readStatement = connection.prepareStatement(getLikes);
            ResultSet resultSet = readStatement.executeQuery();
            if (!resultSet.next()) {
                return Result.error(NOT_FOUND);
            }
            Likes l = new Likes();
            l.setOwnerId(resultSet.getString("ownerId"));
            l.setShortId(resultSet.getString("shortId"));
            l.setUserId(resultSet.getString("userId"));
            return Result.ok(l);
        }
        else if(clazz.equals(Following.class)) {
            String getFollowing = format("SELECT * FROM following WHERE id = '%s';", id);
            PreparedStatement readStatement = connection.prepareStatement(getFollowing);
            ResultSet resultSet = readStatement.executeQuery();
            if (!resultSet.next()) {
                return Result.error(NOT_FOUND);
            }
            Following f = new Following();
            f.setFollower(resultSet.getString("follower"));
            f.setFollowee(resultSet.getString("followee"));
            return Result.ok(f);
        }
        else {
            String getFollowing = format("SELECT * FROM shorts WHERE shortId = '%s';", id);
            PreparedStatement readStatement = connection.prepareStatement(getFollowing);
            ResultSet resultSet = readStatement.executeQuery();
            if (!resultSet.next()) {
                return Result.error(NOT_FOUND);
            }

            Short s = new Short();
            s.setShortId(resultSet.getString("shortId"));
            s.setOwnerId(resultSet.getString("ownerId"));
            s.setBlobUrl(resultSet.getString("blobUrl"));
            s.setTimestamp(resultSet.getLong("timestamp"));
            s.setTotalLikes(resultSet.getInt("totalLikes"));
            return Result.ok(s);
        }
    }

    @Override
    public <T> Result<T> persistOne(T obj) {
        try {
            return insertOne(obj);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        try {
            return update(obj);
        }
        catch(SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T> Result<?> deleteOne(T obj) {
        try {
            return delete(obj);
        }
        catch(SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T> void deleteAll(Class<T> clazz, Session s, String... args) {
        // TODO
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

    @Override
    public <T> Result<List<T>> getAll(Class<T> clazz, String container, String... args) {
        return null;
    }

    private <T> Result<List<T>> count(Class<T> clazz, String container, String attribute, String id) throws SQLException {
        String query = format("SELECT COUNT(%s) FROM %s WHERE %s = '%s'", attribute, container, attribute, id);
        System.out.println(query + " AAAAAAAAAAAAAAAAAAAAAAAAAAA");
        PreparedStatement readStatement = connection.prepareStatement(query);
        ResultSet resultSet = readStatement.executeQuery();
        if (!resultSet.next()) {
            return Result.error(NOT_FOUND);
        }

        //BUG ALGURES AQUI

        int count = resultSet.getInt(1);
        List<T> resultList = new ArrayList<>();
        resultList.add(clazz.cast(count));
        return Result.ok(resultList);
    }


    @Override
    public <T> Result<List<T>> countAll(Class<T> clazz, String container, String attribute, String id) {
        try {
            count(clazz, container, attribute, id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match) {
        return null;
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<T> execute(Consumer<Session> proc) {
        return Result.error(NOT_IMPLEMENTED);
    }

    @Override
    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        return Result.error(NOT_IMPLEMENTED);
    }

    @Override
    public <T> Result<List<T>> searchPattern(Class<T> clazz, String pattern, String container, String attribute) {
        return null;
    }
}
