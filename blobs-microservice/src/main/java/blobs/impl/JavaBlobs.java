package blobs.impl;

import blobs.api.Blobs;
import blobs.api.Result;
import blobs.impl.cookies.Authentication;
import blobs.impl.rest.BlobsMicroService;
import blobs.impl.storage.blobs.AzureBlobStorage;
import blobs.impl.storage.blobs.BlobStorage;
import blobs.impl.storage.blobs.FilesystemStorage;
import utils.Hash;
import utils.Hex;

import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.lang.String.format;
import static blobs.api.Result.ErrorCode.FORBIDDEN;
import static blobs.api.Result.error;
import static blobs.api.Result.errorOrValue;
import static blobs.impl.storage.blobs.BlobsType.AZURE_BLOBS;

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
		baseURI = String.format("%s/%s/", BlobsMicroService.PRIMARY_BASE_URI, Blobs.NAME);
	}


	private BlobStorage initStorage() {
		return BlobsMicroService.BLOBS_TYPE.equals(AZURE_BLOBS) ? new AzureBlobStorage() : new FilesystemStorage();
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

		System.out.println("AQUIIII");

		if (!validBlobId(blobId, token)) {
			System.out.println("LALALALALA");
			return error(FORBIDDEN);
		}

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
			Authentication.validateSession(BlobsMicroService.ADMIN);
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
			Authentication.validateSession(BlobsMicroService.ADMIN);
		} catch (Exception e) {
			return error(FORBIDDEN);
		}
		return deleteBlobs(userId, pwd);
	}

	public static Result<Void> deleteBlobs(String userId, String pwd) {
		//TODO
		//return errorOrValue(okUser(userId, pwd), storage.delete(toPath(userId)) );
		return storage.delete(toPath(userId));

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