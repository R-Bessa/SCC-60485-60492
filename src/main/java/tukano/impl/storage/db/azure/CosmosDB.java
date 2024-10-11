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
    private static CosmosContainer users_container;
    private static CosmosContainer shorts_container;
    private static CosmosContainer follow_container;
    private static CosmosContainer likes_container;



    public CosmosDB(CosmosClient client) {
        this.client = client;
        init();
    }


    private synchronized void init() {
        if( db != null)
            return;

        db = createDatabaseIfNotExists(client);

        createContainerIfNotExists(USERS_CONTAINER);
        users_container = db.getContainer(USERS_CONTAINER);

        createContainerIfNotExists(SHORTS_CONTAINER);
        shorts_container = db.getContainer(SHORTS_CONTAINER);

        createContainerIfNotExists(FOLLOW_CONTAINER);
        follow_container = db.getContainer(FOLLOW_CONTAINER);

        createContainerIfNotExists(LIKES_CONTAINER);
        likes_container = db.getContainer(LIKES_CONTAINER);
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

    private <T> Result<CosmosContainer> getContainerWithObj(T obj) {
        if (obj instanceof User)
            return Result.ok(users_container);
        if(obj instanceof Likes)
            return Result.ok(likes_container);
        if(obj instanceof Following)
            return Result.ok(follow_container);
        else
            return Result.ok(shorts_container);
    }

    private <T> Result<CosmosContainer> getContainerWithClass(Class<T> clazz) {
        if(clazz.equals(User.class))
            return Result.ok(users_container);
        if(clazz.equals(Long.class))
            return Result.ok(likes_container);
        if(clazz.equals(Following.class))
            return Result.ok(follow_container);
        else
            return Result.ok(shorts_container);
    }

    @Override
    public <T> Result<T>  persistOne(T obj) {
        var container = getContainerWithObj(obj).value();
        return tryCatch( () -> container.createItem(obj).getItem());
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        var container = getContainerWithObj(obj).value();
        return tryCatch( () -> container.upsertItem(obj).getItem());
    }

    @Override
    public <T> Result<?> deleteOne(T obj) {
        // TODO
        var container = getContainerWithObj(obj).value();
        return tryCatch( () -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        var container = getContainerWithClass(clazz).value();
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        var container = getContainerWithClass(clazz).value();
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
}
