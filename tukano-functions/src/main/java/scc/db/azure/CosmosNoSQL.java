package scc.db.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import org.hibernate.Session;
import scc.data.Following;
import scc.data.Likes;
import scc.data.User;
import scc.db.Database;
import scc.utils.Result;
import scc.utils.Result.ErrorCode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static scc.db.DB.*;
import static scc.serverless.HttpFunction.CONNECTION_URL;
import static scc.serverless.HttpFunction.DB_KEY;
import static scc.utils.Result.ErrorCode.NOT_IMPLEMENTED;
import static scc.utils.Result.error;

public class CosmosNoSQL implements Database {

    private static final String DB_NAME = "tukano-db-60485";
    private static final String PARTITION_KEY_PATH = "/id";


    private static CosmosNoSQL instance;
    private final CosmosClient client;
    private static CosmosDatabase db;
    public static CosmosContainer users_container;
    public static CosmosContainer shorts_container;
    public static CosmosContainer following_container;
    public static CosmosContainer likes_container;



    public CosmosNoSQL(CosmosClient client) {
        this.client = client;
    }


    public synchronized void init(String container_name) {
        if(container_name.equals(USERS)) {
            createContainerIfNotExists(USERS);
            users_container = db.getContainer(USERS);
        }

        else if(container_name.equals(SHORTS)) {
            createContainerIfNotExists(SHORTS);
            shorts_container = db.getContainer(SHORTS);

            createContainerIfNotExists(FOLLOWING);
            following_container = db.getContainer(FOLLOWING);

            createContainerIfNotExists(LIKES);
            likes_container = db.getContainer(LIKES);
        }
    }


    synchronized public static CosmosNoSQL getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                .directMode()
                //.gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true) //
                .buildClient();

        db = createDatabaseIfNotExists(client);
        instance = new CosmosNoSQL(client);
        return instance;
    }

    private static CosmosDatabase createDatabaseIfNotExists(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(DB_NAME);
        return cosmosClient.getDatabase(DB_NAME);
    }

    private static void createContainerIfNotExists(String containerName) {
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(
                containerName,
                PARTITION_KEY_PATH
        );

        ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(400);
        db.createContainerIfNotExists(
                containerProperties,
                throughputProperties
        );
    }


    @Override
    public <T> Result<T> persistOne(T obj) {
        var container = getContainerByObj(obj).value();
        return tryCatch( () -> container.createItem(obj).getItem());
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        var container = getContainerByObj(obj).value();
        return tryCatch( () -> container.upsertItem(obj).getItem());
    }

    @Override
    public <T> Result<?> deleteOne(T obj) {
        var container = getContainerByObj(obj).value();
        return tryCatch( () -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    @Override
    public <T> void deleteAll(Class<T> clazz, Session s, String... args) {
        var container = getContainerByClass(clazz).value().getId();
        List<T> toDelete = getAll(clazz, container, args).value();
        for(T obj: toDelete)
            deleteOne(obj);
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        var container = getContainerByClass(clazz).value();
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> Result<List<T>> getAll(Class<T> clazz, String container, String... args) {
        String query;
        if(args.length == 2)
            query = format("SELECT * FROM %s WHERE %s.%s = \"%s\"", container, container, args[0], args[1]);
        else
            query = format("SELECT * FROM %s WHERE %s.%s = \"%s\" OR %s.%s = \"%s\"",
                    container, container, args[0], args[1], container, args[2], args[3]);

        return sql(query, clazz);
    }

    @Override
    public <T> Result<List<T>> countAll(Class<T> clazz, String container, String attribute, String id) {
        String query = format("SELECT VALUE COUNT(l.%s) FROM %s l WHERE l.%s = \"%s\"", attribute, container, attribute, id);
        return sql(query, clazz);
    }

    @Override
    public <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match) {
        var query = format("SELECT VALUE %s.%s FROM %s WHERE %s.%s = \"%s\"", container, attribute, container, container, param, match);
        return sql(query, clazz);
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        var container = getContainerByQuery(sqlStatement).value();
        return tryCatch(() -> {
            var res = container.queryItems(sqlStatement, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    @Override
    public <T> Result<T> execute(Consumer<Session> proc) {
        return error(NOT_IMPLEMENTED);
    }

    @Override
    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        return error(NOT_IMPLEMENTED);
    }

    @Override
    public <T> Result<List<T>> searchPattern(Class<T> clazz, String pattern, String container, String attribute) {;
        String query = format("SELECT * FROM %s u WHERE UPPER(u.%s) LIKE '%%%s%%'", container, attribute, pattern.toUpperCase());
        return sql(query, clazz);
    }

    <T> Result<T> tryCatch( Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            ce.printStackTrace();
            return error ( errorCodeFromStatus(ce.getStatusCode() ));
        } catch( Exception x ) {
            x.printStackTrace();
            return error( Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    static Result.ErrorCode errorCodeFromStatus(int status ) {
        return switch( status ) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private <T> Result<CosmosContainer> getContainerByObj(T obj) {
        return getContainerByClass(obj.getClass());
    }

    private Result<CosmosContainer> getContainerByClass(Class<?> clazz) {
        if (clazz.equals(User.class))
            return Result.ok(users_container);
        if (clazz.equals(Likes.class) || clazz.equals(Long.class))
            return Result.ok(likes_container);
        if (clazz.equals(Following.class))
            return Result.ok(following_container);
        else
            return Result.ok(shorts_container);
    }

    private Result<CosmosContainer> getContainerByQuery(String query) {
        if (query.contains(USERS))
            return Result.ok(users_container);
        if (query.contains(LIKES))
            return Result.ok(likes_container);
        if (query.contains(FOLLOWING))
            return Result.ok(following_container);
        else
            return Result.ok(shorts_container);
    }

}