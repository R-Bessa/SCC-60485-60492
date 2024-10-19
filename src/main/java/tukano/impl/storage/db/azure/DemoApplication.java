package tukano.impl.storage.db.azure;

import tukano.api.Short;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;

import java.sql.*;
import java.util.Scanner;
import java.util.logging.Logger;

public class DemoApplication {

    private static final Logger log;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-7s] %5$s %n");
        log =Logger.getLogger(DemoApplication.class.getName());
    }
    public static void main(String[] args)throws Exception
    {
        log.info("Connecting to the database");
        Connection connection = DButil.getDataSource().getConnection();
        System.out.println("The Connection Object is of Class: " + connection.getClass());
        log.info("Database connection test: " + connection.getCatalog());
        log.info("Creating table");
        log.info("Creating index");
        log.info("distributing table");
        Scanner scanner = new Scanner(DemoApplication.class.getClassLoader().getResourceAsStream("schema.sql"));
        Statement statement = connection.createStatement();
        while (scanner.hasNextLine()) {
            statement.execute(scanner.nextLine());
        }

        User todo = new User("walesID", "pwd", "email", "Wales");
        User liskov = new User("liskovID", "pwd", "email", "Liskov");
        Short s = new Short("id", "walesID", "url", 0, 0);
        Following f = new Following("walesID", "liskovID");
        Likes l = new Likes("liskovID", "id", "walesID");

        insertData(todo, liskov, s, f, l, connection);

        readData(connection);

        log.info("Closing database connection");
        connection.close();
    }

    private static void insertData(User todo, User liskov, Short s, Following f, Likes l, Connection connection) throws SQLException {
        log.info("Insert data");
        PreparedStatement insertStatement = connection
                .prepareStatement("INSERT INTO users  VALUES (?, ?, ?, ?);");

        insertStatement.setString(1, todo.getUserId());
        insertStatement.setString(2, todo.pwd());
        insertStatement.setString(3, todo.email());
        insertStatement.setString(4, todo.displayName());
        insertStatement.executeUpdate();

        PreparedStatement insertStatement5 = connection
                .prepareStatement("INSERT INTO users  VALUES (?, ?, ?, ?);");
        insertStatement5.setString(1, liskov.getUserId());
        insertStatement5.setString(2, liskov.pwd());
        insertStatement5.setString(3, liskov.email());
        insertStatement5.setString(4, liskov.displayName());
        insertStatement5.executeUpdate();

        PreparedStatement insertStatement2 = connection.prepareStatement("INSERT INTO shorts VALUES (?, ?, ?, ?, ?)");
        insertStatement2.setString(1, s.getShortId());
        insertStatement2.setString(2, s.getOwnerId());
        insertStatement2.setString(3, s.getBlobUrl());
        insertStatement2.setLong(4, s.getTimestamp());
        insertStatement2.setInt(5, s.getTotalLikes());
        insertStatement2.executeUpdate();

        PreparedStatement insertStatement3 = connection.prepareStatement("INSERT INTO following VALUES (?, ?, ?)");
        insertStatement3.setString(1, f.getId());
        insertStatement3.setString(2, f.getFollower());
        insertStatement3.setString(3, f.getFollowee());
        insertStatement3.executeUpdate();

        PreparedStatement insertStatement4;
        insertStatement4 = connection.prepareStatement("INSERT INTO likes VALUES (?, ?, ?, ?)");
        insertStatement4.setString(1, l.getId());
        insertStatement4.setString(2, l.getUserId());
        insertStatement4.setString(3, l.getShortId());
        insertStatement4.setString(4, l.getOwnerId());
        insertStatement4.executeUpdate();
    }

    private static User readData(Connection connection) throws SQLException {
        log.info("Read data");
        PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM public.users;");
        PreparedStatement readStatement2 = connection.prepareStatement("SELECT * FROM public.shorts;");
        PreparedStatement readStatement3 = connection.prepareStatement("SELECT * FROM public.following;");
        PreparedStatement readStatement4 = connection.prepareStatement("SELECT * FROM public.likes;");
        ResultSet resultSet = readStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return null;
        }
        ResultSet resultSet2 = readStatement2.executeQuery();
        if (!resultSet2.next()) {
            log.info("There is no data in the database!");
            return null;
        }
        ResultSet resultSet3 = readStatement3.executeQuery();
        if (!resultSet3.next()) {
            log.info("There is no data in the database!");
            return null;
        }
        ResultSet resultSet4 = readStatement4.executeQuery();
        if (!resultSet4.next()) {
            log.info("There is no data in the database!");
            return null;
        }

        User todo = new User();
        todo.setUserId(resultSet.getString("userId"));
        todo.setPwd(resultSet.getString("pwd"));
        todo.setEmail(resultSet.getString("email"));
        todo.setDisplayName(resultSet.getString("displayName"));
        log.info("User read from the database: " + todo.toString());


        Short s = new Short();
        s.setShortId(resultSet2.getString("shortId"));
        s.setOwnerId(resultSet2.getString("ownerId"));
        s.setBlobUrl(resultSet2.getString("blobUrl"));
        s.setTimestamp(resultSet2.getLong("timestamp"));
        s.setTotalLikes(resultSet2.getInt("totalLikes"));
        log.info("Short read from the database: " + s.toString());

        Following f = new Following();
        f.setFollower(resultSet3.getString("follower"));
        f.setFollowee(resultSet3.getString("followee"));
        log.info("Following read from the database: " + f.toString());

        Likes l = new Likes();
        l.setOwnerId(resultSet4.getString("ownerId"));
        l.setShortId(resultSet4.getString("shortId"));
        l.setUserId(resultSet4.getString("userId"));
        log.info("Likes read from the database: " + l.toString());

        return todo;
    }

}