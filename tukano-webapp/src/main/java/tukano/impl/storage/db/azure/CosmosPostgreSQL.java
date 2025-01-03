package tukano.impl.storage.db.azure;


import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.Session;
import org.postgresql.util.PSQLException;
import tukano.api.Result;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.data.User;
import tukano.impl.data.Short;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.Database;

import javax.sql.DataSource;
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
import static tukano.api.Result.ErrorCode.*;
import static tukano.impl.rest.TukanoApplication.POSTGRES_URL;

public class CosmosPostgreSQL implements Database {

    private static CosmosPostgreSQL instance;

    //COSMOS
    private static final String DB_USERNAME = "db.username";
    private static final String DB_PASSWORD = "db.password";
    private static final String DB_URL = "db.url";
    private static final String DB_DRIVER_CLASS = "driver.class.name";

    private static DataSource datasource;
    private static Connection connection;

    synchronized public static CosmosPostgreSQL getInstance() {
        if (instance == null) {
            instance = new CosmosPostgreSQL();
            if (!TukanoApplication.DOCKER_POSTGRES_ON)
                init();
        }
        return instance;
    }

    private static void init() {
        try {
            datasource = getDataSource();
            connection = datasource.getConnection();
            Scanner scanner = new Scanner(CosmosPostgreSQL.class.getClassLoader().getResourceAsStream("schema.sql"));
            Statement statement = connection.createStatement();
            while (scanner.hasNextLine()) {
                statement.execute(scanner.nextLine());
            }
            connection.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws SQLException {
        if(TukanoApplication.DOCKER_POSTGRES_ON) {
            try {
                Connection conn = null;
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(POSTGRES_URL);
                return conn;
            } catch (Exception e) {
                System.out.println("Failed to create JDBC db connection " + e.toString() + e.getMessage());
            }
        }
        else return datasource.getConnection();
        return null;
    }

    private static DataSource getDataSource() {
        try {
            Properties properties = new Properties();
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
            ds.setMinimumIdle(10);
            ds.setMaximumPoolSize(100000);
            ds.setAutoCommit(true);
            ds.setLoginTimeout(10);

            return ds;

        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void executeQuery(String query) throws SQLException {
        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(query)) {
            statement.executeUpdate();
        }
    }

    private <T> Result<T> insertOne(T obj) throws SQLException {
        if (obj instanceof User u) {
            String insertUser = format("INSERT INTO users VALUES ('%s', '%s', '%s', '%s');", u.getUserId(), u.getPwd(), u.getEmail(), u.getDisplayName());
            executeQuery(insertUser);
        }
        else if (obj instanceof Likes l) {
            String insertLike = format("INSERT INTO likes VALUES ('%s', '%s', '%s', '%s');", l.getId(), l.getUserId(), l.getShortId(), l.getOwnerId());
            executeQuery(insertLike);
        }
        else if (obj instanceof Following f) {
            String insertLike = format("INSERT INTO following VALUES ('%s', '%s', '%s');", f.getId(), f.getFollower(), f.getFollowee());
            executeQuery(insertLike);
        }
        else {
            Short s = (Short)obj;
            String insertShort = format("INSERT INTO shorts VALUES ('%s', '%s', '%s', '%s', '%s', 0);", s.getShortId(), s.getOwnerId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes());
            executeQuery(insertShort);
        }
        return Result.ok(obj);
    }

    private <T> Result<T> update(T obj) throws SQLException {
        if (obj instanceof User u) {
            String updateUser = format("UPDATE users SET pwd = '%s', email= '%s', displayName = '%s' WHERE userId = '%s'" , u.getPwd(), u.getEmail(), u.getDisplayName(), u.getUserId());
            executeQuery(updateUser);
        }
        else if (obj instanceof Likes l) {
            String updateLikes = format("UPDATE likes SET userid = '%s', shortId = '%s', ownerId = '%s' WHERE id='%s'", l.getUserId(), l.getShortId(), l.getOwnerId(), l.getId());
            executeQuery(updateLikes);
        }
        else if (obj instanceof Following f) {
            String updateFollowing = format("UPDATE following SET follower = '%s', followee = '%s' WHERE id = '%s'", f.getFollower(), f.getFollowee(), f.getId());
            executeQuery(updateFollowing);
        }
        else {
            Short s = (Short)obj;
            String updateShort = format("UPDATE shorts SET ownerId = '%s', blobUrl = '%s', timestamp = '%s', totalLikes = '%s', views = '%s' WHERE shortId = '%s'", s.getOwnerId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getViews(), s.getShortId());
            executeQuery(updateShort);
        }
        return Result.ok(obj);
    }

    private void deleteAux(String table, String attribute, String id) throws SQLException {
        String delete = format("DELETE FROM %s WHERE %s = '%s'", table, attribute, id);
        try (Connection connection = getConnection();
             PreparedStatement deleteStatement = connection.prepareStatement(delete)) {
             deleteStatement.executeUpdate();
        }
    }

    private <T> Result<T> delete(T obj) throws SQLException {
        if (obj instanceof User u)
            deleteAux("users", "userId", u.getUserId());
        else if (obj instanceof Likes l)
            deleteAux("likes", "id", l.getId());
        else if (obj instanceof Following f)
            deleteAux("following", "id", f.getId());
        else {
            Short s = (Short)obj;
            deleteAux("shorts", "shortId", s.getShortId());
        }
        return Result.ok(obj);
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
        else if (clazz.equals(Likes.class)) {
            try (Connection connection = getConnection();
                 PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM likes WHERE id = ?;")) {
                 readStatement.setString(1, id);
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

        }
        else if(clazz.equals(Following.class)) {
            try (Connection connection = getConnection();
                 PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM following WHERE id = ?;")) {
                 readStatement.setString(1, id);
                 ResultSet resultSet = readStatement.executeQuery();
                 if (!resultSet.next()) {
                     return Result.error(NOT_FOUND);
                 }
                 Following f = new Following();
                 f.setFollower(resultSet.getString("follower"));
                 f.setFollowee(resultSet.getString("followee"));
                 return Result.ok(f);
            }

        }
        else {
            String query = format("SELECT * FROM public.shorts WHERE shortId = '%s';", id);
            try (Connection connection = getConnection();
                PreparedStatement readStatement = connection.prepareStatement(query)) {
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
                s.setViews(resultSet.getInt("views"));
                return Result.ok(s);
            }
        }
    }

    @Override
    public <T> Result<T> persistOne(T obj) {
        try {
            return insertOne(obj);
        }
        catch (PSQLException e) {
            if (e.getSQLState().equals("23505")) {
                System.out.println("Duplicate key error: " + e.getMessage());
                return Result.error(CONFLICT);
            }
        } catch (SQLException e) {
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
        try {
            deleteAux(args[0], args[1], args[2]);
        }
        catch (SQLException e ) {
            e.printStackTrace();
        }
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
        return Result.error(NOT_IMPLEMENTED);
    }

    private <T> Result<List<T>> count(Class<T> clazz, String container, String attribute, String id) throws SQLException {
        String query = format("SELECT COUNT(%s) FROM %s WHERE %s = '%s'", attribute, container, attribute, id);
        try (Connection connection = getConnection();
            PreparedStatement readStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = readStatement.executeQuery();
            if (!resultSet.next()) {
                return Result.error(NOT_FOUND);
            }

            int count = resultSet.getInt(1);
            List<T> resultList = new ArrayList<>();
            resultList.add(clazz.cast((long)count));
            return Result.ok(resultList);
        }
    }


    @Override
    public <T> Result<List<T>> countAll(Class<T> clazz, String container, String attribute, String id) {
        try {
            return count(clazz, container, attribute, id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Result<List<T>> getAllByAttributeAux(String container, String attribute, String param, String match) throws SQLException {
        var query = format("SELECT %s FROM %s WHERE %s = '%s'", attribute, container, param, match);
        try (Connection connection = getConnection();
            PreparedStatement readStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = readStatement.executeQuery();
            List<T> resultList = new ArrayList<>();
            while (resultSet.next()) {
                String result = resultSet.getString(1);
                resultList.add((T) result);
            }

            return Result.ok(resultList);
        }
    }

    @Override
    public <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match) {
        try {
            return getAllByAttributeAux(container, attribute, param, match);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T> Result<List<T>> sqlAux(String query) throws SQLException {
        try (Connection connection = getConnection();
            PreparedStatement readStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = readStatement.executeQuery();
            List<T> resultList = new ArrayList<>();
            while (resultSet.next()) {
                String result1 = resultSet.getString(1);
                String result2 = resultSet.getString(2);

                resultList.add((T) result1);
                resultList.add((T) result2);
            }

            return Result.ok(resultList);
        }
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        try {
            return sqlAux(sqlStatement);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
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

    private <T> Result<List<T>> searchPatternAux(Class<T> clazz, String pattern, String container, String attribute) throws SQLException {
        String query = format("SELECT * FROM %s u WHERE UPPER(%s) LIKE '%%%s%%'", container, attribute, pattern.toUpperCase());
        try (Connection connection = getConnection();
            PreparedStatement readStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = readStatement.executeQuery();
            List<T> resultList = new ArrayList<>();
            while (resultSet.next()) {
                String userId = resultSet.getString(1);
                String pwd = resultSet.getString(2);
                String email = resultSet.getString(3);
                String displayName = resultSet.getString(4);
                User u = new User(userId, pwd, email, displayName);
                resultList.add((T) u);
            }

            return Result.ok(resultList);
        }
    }

    @Override
    public <T> Result<List<T>> searchPattern(Class<T> clazz, String pattern, String container, String attribute) {
        try {
            return searchPatternAux(clazz, pattern, container, attribute);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Result<List<Short>> searchPopularAux() throws SQLException {
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        String query = String.format(
                "SELECT * FROM public.shorts WHERE timestamp >= %d AND shortId NOT LIKE 'tukano%%' ORDER BY views DESC LIMIT 1",
                fiveMinutesAgo);
        try (Connection connection = getConnection();
             PreparedStatement readStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = readStatement.executeQuery();

            List<Short> resultList = new ArrayList<>();
            while (resultSet.next()) {
                Short s = new Short();
                s.setShortId(resultSet.getString("shortId"));
                s.setOwnerId(resultSet.getString("ownerId"));
                s.setBlobUrl(resultSet.getString("blobUrl"));
                s.setTimestamp(resultSet.getLong("timestamp"));
                s.setTotalLikes(resultSet.getInt("totalLikes"));
                s.setViews(resultSet.getInt("views"));
                System.out.println(s + " SHOOORT");
                resultList.add(s);
            }
            return Result.ok(resultList);
        }
    }

    @Override
    public Result<List<Short>> searchPopular() {
            try {
                return searchPopularAux();
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
    }


}
