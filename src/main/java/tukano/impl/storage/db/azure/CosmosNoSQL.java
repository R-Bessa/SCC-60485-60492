package tukano.impl.storage.db.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import org.hibernate.Session;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.Database;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.NOT_IMPLEMENTED;
import static tukano.api.Result.error;
import static tukano.impl.storage.db.DB.*;

public class CosmosNoSQL implements Database {

    private static final String DB_NAME = "tukano-db-60485";
    private static final String PARTITION_KEY_PATH = "/id";


    private static CosmosNoSQL instance;
    private final CosmosClient client;
    private static CosmosDatabase db;
    private static CosmosContainer users;
    private static CosmosContainer shorts;
    private static CosmosContainer following;
    private static CosmosContainer likes;



    public CosmosNoSQL(CosmosClient client) {
        this.client = client;
    }


    public synchronized void init(String container_name) {
        if(container_name.equals(USERS)) {
            createContainerIfNotExists(USERS);
            users = db.getContainer(USERS);
        }

        else if(container_name.equals(SHORTS)) {
            createContainerIfNotExists(SHORTS);
            shorts = db.getContainer(SHORTS);

            createContainerIfNotExists(FOLLOWING);
            following = db.getContainer(FOLLOWING);

            createContainerIfNotExists(LIKES);
            likes = db.getContainer(LIKES);
        }
    }


    synchronized public static CosmosNoSQL getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(TukanoApplication.CONNECTION_URL)
                .key(TukanoApplication.DB_KEY)
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
    public <T> Result<T>  persistOne(T obj) {
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
        return Result.error(NOT_IMPLEMENTED);
    }

    @Override
    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        return Result.error(NOT_IMPLEMENTED);
    }


    <T> Result<T> tryCatch( Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            ce.printStackTrace();
            return error ( errorCodeFromStatus(ce.getStatusCode() ));
        } catch( Exception x ) {
            x.printStackTrace();
            return error( ErrorCode.INTERNAL_ERROR);
        }
    }

    static ErrorCode errorCodeFromStatus( int status ) {
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
            return Result.ok(users);
        if (clazz.equals(Likes.class) || clazz.equals(Long.class))
            return Result.ok(likes);
        if (clazz.equals(Following.class))
            return Result.ok(following);
        else
            return Result.ok(shorts);
    }

    private Result<CosmosContainer> getContainerByQuery(String query) {
        if (query.contains(USERS))
            return Result.ok(users);
        if (query.contains(LIKES))
            return Result.ok(likes);
        if (query.contains(FOLLOWING))
            return Result.ok(following);
        else
            return Result.ok(shorts);
    }





}
