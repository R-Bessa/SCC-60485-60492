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
import tukano.impl.storage.db.DB;
import tukano.impl.storage.db.Database;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static tukano.api.Result.error;

public class CosmosDB implements Database {

    private static final String DB_NAME = "tukano-db-60485";
    private static final String USERS_CONTAINER = "users";
    private static final String SHORTS_CONTAINER = "shorts";
    private static final String FOLLOW_CONTAINER = "follow";
    private static final String LIKES_CONTAINER = "likes";
    private static final String PARTITION_KEY_PATH = "/id";


    private static CosmosDB instance;
    private CosmosClient client;
    private static CosmosDatabase db;
    private static CosmosContainer users;
    private static CosmosContainer shorts;
    private static CosmosContainer follow;
    private static CosmosContainer likes;



    public CosmosDB(CosmosClient client) {
        this.client = client;
        init();
    }


    private synchronized void init() {
        if( db != null)
            return;

        db = createDatabaseIfNotExists(client);

        createContainerIfNotExists(USERS_CONTAINER);
        users = db.getContainer(USERS_CONTAINER);

        createContainerIfNotExists(SHORTS_CONTAINER);
        shorts = db.getContainer(SHORTS_CONTAINER);

        createContainerIfNotExists(FOLLOW_CONTAINER);
        follow = db.getContainer(FOLLOW_CONTAINER);

        createContainerIfNotExists(LIKES_CONTAINER);
        likes = db.getContainer(LIKES_CONTAINER);
    }


    synchronized public static CosmosDB getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(TukanoApplication.CONNECTION_URL)
                .key(TukanoApplication.DB_KEY)
                //.directMode()
                .gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true) //
                .buildClient();

        instance = new CosmosDB(client);
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
    public <T> void deleteAllConditional(Class<T> clazz, Session s, String... args) {
        String query, container = getContainerByClass(clazz).value().getId();

        if(args.length == 2)
            query = format("SELECT * FROM %s WHERE %s.%s = \"%s\"", container, container, args[0], args[1]);
        else
            query = format("SELECT * FROM %s WHERE %s.%s = \"%s\" OR %s.%s = \"%s\"",
                    container, container, args[0], args[1], container, args[2], args[3]);

        List<T> toDelete = sql(query, clazz).value();
        for(T obj: toDelete)
            DB.deleteOne( obj );
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        var container = getContainerByClass(clazz).value();
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        var container = getContainerByClass(clazz).value();
        return tryCatch(() -> {
            var res = container.queryItems(sqlStatement, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    @Override
    public <T> Result<T> execute(Consumer<Session> proc) {
        return null;
    }

    @Override
    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        return null;
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
            return Result.ok(follow);
        else
            return Result.ok(shorts);
    }
}
