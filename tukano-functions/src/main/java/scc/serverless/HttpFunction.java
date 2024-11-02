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
import scc.data.Blob;
import scc.utils.RedisCache;
import scc.utils.Token;

import java.util.Optional;

import static scc.utils.RedisCache.REDIS_CACHE_ON;
import static scc.utils.RedisCache.VIEWS_KEY_PREFIX;

public class HttpFunction {
    public static final String TUKANO_SECRET = "tukano_app_secret";
    public static String baseURI;
    public static final String BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
    //public static final String BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";
    private static final String BLOBS = "blobs";
	private static final String BLOB_ID = "blobId";

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

    private BlobContainerClient containerClient;
    private static final int BLOB_CONFLICT = 409;
    private static final int BLOB_NOT_FOUND = 404;
    private static final String VIDEOS_CONTAINER = "videos";
    private static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=2lddvpV/kKYzpiUq6yOzg52AyB599d1OyeJQf694VGMrr0UbRjIj6Rp3Ns/bsm7htNWCmmwkcDSl+AStQ1GPyg==;EndpointSuffix=core.windows.net";
    //public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";


    private void initContainerClient() {
        containerClient = new BlobServiceClientBuilder()
                .connectionString(BLOB_STORAGE_KEY)
                .buildClient()
                .createBlobContainerIfNotExists(VIDEOS_CONTAINER);

        containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
        Token.setSecret(TUKANO_SECRET);
        baseURI = String.format("%s/%s/", BASE_URI, BLOBS);

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

        initContainerClient();

        String token = request.getQueryParameters().get("token");

        if( ! validBlobId( blobId, token ) )
            return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

        if (blobId == null)
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

        if(request.getBody().isEmpty())
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

        byte[] bytes = request.getBody().get().getBytes();

        String path = toPath(blobId);

        var blob = containerClient.getBlobClient(path);
        var data = BinaryData.fromBytes(bytes);

        try {
            blob.upload(data);
            var owner = path.split("/")[0];
            RedisCache.addRecentBlob(new Blob(blobId, owner, bytes));

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

        initContainerClient();

        String token = request.getQueryParameters().get("token");

        if (!validBlobId(blobId, token))
            return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

        if (blobId == null)
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

        var blobData = RedisCache.getRecentBlob(blobId);
        if(blobData != null) {
            RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);
            return request.createResponseBuilder(HttpStatus.OK).body(blobData.getBytes()).build();
        }

        String path = toPath(blobId);
        byte[] bytes = null;
        var blob = containerClient.getBlobClient(path);
        var owner = path.split("/")[0];

        try {
            BinaryData data = blob.downloadContent();
            bytes = data.toBytes();
            RedisCache.addRecentBlob(new Blob(blobId, owner, bytes));

        } catch (BlobStorageException e) {
            if (e.getStatusCode() == BLOB_NOT_FOUND)
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
        }

        if (bytes != null) {
            if(!REDIS_CACHE_ON)
                System.out.println(); // TODO DB.updateViews(blobId, 1);
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

        initContainerClient();

        String token = request.getQueryParameters().get("token");

        if( ! validBlobId( blobId, token ) )
            return request.createResponseBuilder(HttpStatus.FORBIDDEN).build();

        if (blobId == null)
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();

        String path = toPath(blobId);

        var blob = containerClient.getBlobClient(path);
        var owner = path.split("/")[0];


        if(blob.deleteIfExists()) {
            RedisCache.removeBlobById(blobId);
            return request.createResponseBuilder(HttpStatus.OK).build();
        }

        else {
            var blobs = containerClient.listBlobsByHierarchy(path + "/");

            if (!blobs.iterator().hasNext())
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();

            else {
                blobs.forEach(blobItem -> {
                    String blobName = blobItem.getName();
                    containerClient.getBlobClient(blobName).delete();
                });

                RedisCache.removeBlobsByOwner(owner);
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

    private String toURL(String blobId ) {
        return baseURI + blobId ;
    }
}
