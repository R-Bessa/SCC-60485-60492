package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.errorOrValue;
import static tukano.impl.JavaShorts.okUser;
import static tukano.impl.storage.blobs.BlobsType.AZURE_BLOBS;

import java.util.function.Consumer;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.cookies.Authentication;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.blobs.BlobStorage;
import tukano.impl.storage.blobs.AzureBlobStorage;
import tukano.impl.storage.blobs.FilesystemStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {
	
	private static Blobs instance;
	private static final Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	public static String baseURI;
	private static BlobStorage storage;
	
	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}
	
	private JavaBlobs() {
		storage = initStorage();
		baseURI = String.format("%s/%s/", TukanoApplication.PRIMARY_BASE_URI, Blobs.NAME);
	}


	private BlobStorage initStorage() {
		return TukanoApplication.BLOBS_TYPE.equals(AZURE_BLOBS) ? new AzureBlobStorage() : new FilesystemStorage();
	}
	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		String userId = blobId.split("\\+")[0];
		try {
			Authentication.validateSession(userId);
		} catch (Exception e) {
			return error(FORBIDDEN);
		}


		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		String userId = blobId.split("\\+")[0];
		try {
			Authentication.validateSession(userId);
		} catch (Exception e) {
			return error(FORBIDDEN);
		}

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

		try {
			Authentication.validateSession(TukanoApplication.ADMIN);
		} catch (Exception e) {
			return error(FORBIDDEN);
		}

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.delete( toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String pwd) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, pwd=%s\n", userId, pwd));
		try {
			Authentication.validateSession(TukanoApplication.ADMIN);
		} catch (Exception e) {
			return error(FORBIDDEN);
		}
		return deleteBlobs(userId, pwd);
	}

		public static Result<Void> deleteBlobs(String userId, String pwd) {
		return errorOrValue(okUser(userId, pwd), storage.delete(toPath(userId)) );
	}

	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}

	private static String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
	
	private String toURL(String blobId ) {
		return baseURI + blobId ;
	}
}
