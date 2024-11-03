package scc;

import scc.api.Blobs;
import scc.storage.blobs.AzureBlobStorage;
import scc.storage.blobs.BlobStorage;
import scc.storage.blobs.FilesystemStorage;
import scc.utils.Hash;
import scc.utils.Hex;
import scc.utils.Result;
import scc.utils.Token;

import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.lang.String.format;
import static scc.JavaShorts.okUser;
import static scc.serverless.HttpFunction.BASE_URI;
import static scc.serverless.HttpFunction.BLOBS_TYPE;
import static scc.storage.blobs.BlobsType.AZURE_BLOBS;
import static scc.utils.Result.ErrorCode.FORBIDDEN;
import static scc.utils.Result.error;
import static scc.utils.Result.errorOrValue;

public class JavaBlobs implements Blobs {
	
	private static Blobs instance;
	private static final Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	public static String baseURI;
	private final BlobStorage storage;
	
	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}
	
	private JavaBlobs() {
		storage = initStorage();
		baseURI = String.format("%s/%s/", BASE_URI, Blobs.NAME);
	}


	private BlobStorage initStorage() {
		return BLOBS_TYPE.equals(AZURE_BLOBS) ? new AzureBlobStorage() : new FilesystemStorage();
	}
	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath( blobId ) );
	}

	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
		Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath(blobId), sink);
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.delete( toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String pwd) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, pwd=%s\n", userId, pwd));

		return errorOrValue(okUser(userId, pwd), storage.delete(toPath(userId)) );
	}
	
	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
	
	private String toURL(String blobId ) {
		return baseURI + blobId ;
	}
}
