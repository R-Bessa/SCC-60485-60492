package tukano.impl.storage.db.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import org.hibernate.Session;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.User;
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
    private static final String PARTITION_KEY_PATH = "/id";


    private static CosmosDB instance;
    private CosmosClient client;
    private static CosmosDatabase db;
    private static CosmosContainer users_container;
    private static CosmosContainer shorts_container;


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
    }


    synchronized public static CosmosDB getInstance() {
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
        var container = obj instanceof User ? users_container : shorts_container;
        return tryCatch( () -> container.createItem(obj).getItem());
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        return null;
    }

    @Override
    public <T> Result<T> deleteOne(T obj) {
        return null;
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        var container = clazz.equals(User.class) ? users_container : shorts_container;
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> List<T> sql(String sqlStatement, Class<T> clazz) {
        return null;
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
