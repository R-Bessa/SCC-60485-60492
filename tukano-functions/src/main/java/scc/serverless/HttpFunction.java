package scc.serverless;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import scc.data.Region;
import scc.db.DB;
import scc.db.DatabaseType;
import scc.utils.RedisCache;
import scc.utils.Token;

import java.util.Optional;
import java.util.concurrent.Executors;

import static scc.utils.RedisCache.VIEWS_KEY_PREFIX;

public class HttpFunction {
    public static final String TUKANO_SECRET = System.getenv("TUKANO_SECRET");
    public static final String TUKANO_RECOMMENDS = "tukano";
    public static String baseURI;

    /** Service Base Uri  */

    public static final String PRIMARY_BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";
    public static final String SECONDARY_BASE_URI = "https://scc-60485-60492-us.azurewebsites.net/rest";
    public static final Region CURRENT_REGION = Region.WEST_EUROPE;

    /** Blob Storage Configs */

    public static final boolean BLOBS_GEO_REPLICATION = true;
    private static final String BLOB_ID = "blobId";
    private static final int BLOB_CONFLICT = 409;
    private static final int BLOB_NOT_FOUND = 404;
    private static final String VIDEOS_CONTAINER = "videos";
    public static final String BLOB_STORAGE_KEY = System.getenv("BLOB_STORAGE_KEY");
    public static final String SECONDARY_BLOB_STORAGE_KEY = System.getenv("SECONDARY_BLOB_STORAGE_KEY");

    /** DB Configs */

    public static final String CONNECTION_URL = System.getenv("CONNECTION_URL");
    public static final String DB_KEY = System.getenv("DB_KEY");
    public static final DatabaseType USERS_DB_TYPE = DatabaseType.COSMOS_DB_NOSQL;
    public static final DatabaseType SHORTS_DB_TYPE = DatabaseType.COSMOS_DB_NOSQL;

    /** Redis Cache Configs */

    public static final boolean REDIS_CACHE_ON = false;
    public static final String REDIS_HOSTNAME = System.getenv("REDIS_HOSTNAME");
    public static final String REDIS_KEY = System.getenv("REDIS_KEY");


    /** Azure Functions Configs */

    //Write
    private static final String HTTP_WRITE_TRIGGER ="writeReq";
	private static final String HTTP_WRITE ="HttpWrite";
	private static final String HTTP_WRITE_TRIGGER_ROUTE ="serverlessBlobs/{" + BLOB_ID + "}";

    //Read
    private static final String HTTP_READ_TRIGGER ="readReq";
    private static final String HTTP_READ ="HttpRead";
    private static final String HTTP_READ_TRIGGER_ROUTE ="serverlessBlobs/{" + BLOB_ID + "}";

    //Delete
    private static final String HTTP_DELETE_TRIGGER ="deleteReq";
    private static final String HTTP_DELETE ="HttpDelete";
    private static final String HTTP_DELETE_TRIGGER_ROUTE ="serverlessBlobs/{" + BLOB_ID + "}";


    private final BlobContainerClient primaryClient = init(BLOB_STORAGE_KEY);
    private final BlobContainerClient secondaryClient = init(SECONDARY_BLOB_STORAGE_KEY);


    private BlobContainerClient init(String key) {
        BlobContainerClient containerClient = new BlobServiceClientBuilder()
                .connectionString(key)
                .buildClient()
                .createBlobContainerIfNotExists(VIDEOS_CONTAINER);
        containerClient.setAccessPolicy(PublicAccessType.BLOB, null);

        return containerClient;
    }


	@FunctionName(HTTP_WRITE)
    public HttpResponseMessage write(
            @HttpTrigger(
                name = HTTP_WRITE_TRIGGER,
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = HTTP_WRITE_TRIGGER_ROUTE)
                HttpRequestMessage<Optional<String>> request,
                @BindingName(BLOB_ID) String blobId,
            final ExecutionContext context) {
                String token = request.getQueryParameters().get("token");
                if( ! validBlobId( blobId, token ) )
                    return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

                if (blobId == null)
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

                if(request.getBody().isEmpty())
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

                String path = toPath(blobId);
                byte[] bytes = request.getBody().get().getBytes();

                var res = execWrite(path, bytes, primaryClient, request);
                if(res.getStatusCode() == 200 && BLOBS_GEO_REPLICATION) {
                    Executors.defaultThreadFactory().newThread(() ->
                            execWrite(path, bytes, secondaryClient, request)).start();
                }

                return res;
            }


    private HttpResponseMessage execWrite(String path, byte[] bytes, BlobContainerClient client,  HttpRequestMessage<Optional<String>> request) {
        var blob = client.getBlobClient(path);
        var data = BinaryData.fromBytes(bytes);

        try {
            blob.upload(data);

        } catch(BlobStorageException e) {

            if (e.getStatusCode() == BLOB_CONFLICT)
                return request.createResponseBuilder(HttpStatus.CONFLICT).build();

            else {
                e.printStackTrace();
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        return request.createResponseBuilder(HttpStatus.OK).build();
    }

    @FunctionName(HTTP_READ)
    public HttpResponseMessage read(
        @HttpTrigger(
                name = HTTP_READ_TRIGGER,
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = HTTP_READ_TRIGGER_ROUTE)
        HttpRequestMessage<Optional<String>> request,
        @BindingName(BLOB_ID) String blobId,
        final ExecutionContext context) {

            BlobContainerClient client;
            if(CURRENT_REGION.equals(Region.WEST_EUROPE))
                client = primaryClient;
            else
                client = secondaryClient;

            String token = request.getQueryParameters().get("token");

            if (!validBlobId(blobId, token))
                return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

            if (blobId == null)
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

            String path = toPath(blobId);
            byte[] bytes = null;
            var blob = client.getBlobClient(path);

            try {
                BinaryData data = blob.downloadContent();
                bytes = data.toBytes();

            } catch (BlobStorageException e) {
                if (e.getStatusCode() == BLOB_NOT_FOUND)
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
            }

            if (bytes != null) {
                if(!REDIS_CACHE_ON)
                    DB.updateViews(blobId, 1);

                else
                    RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);

                return request.createResponseBuilder(HttpStatus.OK).body(bytes).build();
            }

            else
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    @FunctionName(HTTP_DELETE)
    public HttpResponseMessage delete(
            @HttpTrigger(
                    name = HTTP_DELETE_TRIGGER,
                    methods = {HttpMethod.DELETE},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_DELETE_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<String>> request,
            @BindingName(BLOB_ID) String blobId,
            final ExecutionContext context) {

                String token = request.getQueryParameters().get("token");

                if( ! validBlobId( blobId, token ) )
                    return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

                if (blobId == null)
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

                String path = toPath(blobId);
                var res = execDelete(path, primaryClient, request);
                if(res.getStatusCode() == 200 && BLOBS_GEO_REPLICATION) {
                    Executors.defaultThreadFactory().newThread(() ->
                            execDelete(path, secondaryClient, request)).start();
                }

                return res;
            }

    private HttpResponseMessage execDelete(String path, BlobContainerClient client,  HttpRequestMessage<Optional<String>> request) {
        var blob = client.getBlobClient(path);

        if(blob.deleteIfExists())
            return request.createResponseBuilder(HttpStatus.OK).build();

        else {
            var blobs = client.listBlobsByHierarchy(path + "/");

            if (!blobs.iterator().hasNext())
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();

            else {
                blobs.forEach(blobItem -> {
                    String blobName = blobItem.getName();
                    client.getBlobClient(blobName).delete();
                });

                return request.createResponseBuilder(HttpStatus.OK).build();
            }
        }
    }

    private String toPath(String blobId) {
        return blobId.replace("+", "/");
    }

    private boolean validBlobId(String blobId, String token) {
        return Token.isValid(token, blobId);
    }

}
