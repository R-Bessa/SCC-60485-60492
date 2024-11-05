package tukano.impl.storage.blobs;


import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.impl.storage.cache.RedisCache.VIEWS_KEY_PREFIX;


import java.io.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import tukano.api.Result;
import tukano.impl.data.Blob;
import tukano.impl.georeplication.Region;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.cache.RedisCache;
import tukano.impl.storage.db.DB;

public class AzureBlobStorage implements BlobStorage {
	private static final int CHUNK_SIZE = 4096;
	private static final int BLOB_CONFLICT = 409;
	private static final int BLOB_NOT_FOUND = 404;
	private static final String VIDEOS_CONTAINER = "videos";

	// Client Connect with Primary Region Storage
	private final BlobContainerClient containerClient;

	// Replication Client Connected with Secondary Region Storage
	private BlobContainerClient replicationContainerClient;


	public AzureBlobStorage() {
		containerClient = new BlobServiceClientBuilder()
				.connectionString(TukanoApplication.BLOB_STORAGE_KEY)
				.buildClient()
				.createBlobContainerIfNotExists(VIDEOS_CONTAINER);

		containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
	}
	
	@Override
	public Result<Void> write(String path, byte[] bytes) {
		var res = execWrite(path, bytes, TukanoApplication.PRIMARY_REGION);
		if(res.isOK() && TukanoApplication.BLOBS_GEO_REPLICATION) {
			Executors.defaultThreadFactory().newThread(() ->
				execWrite(path, bytes, TukanoApplication.SECONDARY_REGION));
		}
		return res;
	}

	private Result<Void> execWrite(String path, byte[] bytes, Region region) {
		BlobContainerClient client;
		if(region.equals(Region.WEST_EUROPE) || !TukanoApplication.BLOBS_GEO_REPLICATION)
			client = containerClient;
		else
			client = replicationContainerClient;

		if (path == null)
			return error(BAD_REQUEST);

		var blob = client.getBlobClient(path);
		var data = BinaryData.fromBytes(bytes);

		try {
			blob.upload(data);

			var blobId = path.replace("/", "+");
			var owner = path.split("/")[0];
			RedisCache.addRecentBlob(new Blob(blobId, owner, bytes));

		} catch(BlobStorageException e) {

			if (e.getStatusCode() == BLOB_CONFLICT)
				return error(CONFLICT);

			else {
				e.printStackTrace();
				return error(INTERNAL_ERROR);
			}
		}
		return ok();
	}

	@Override
	public Result<byte[]> read(String path) {
		BlobContainerClient client;
		if(TukanoApplication.PRIMARY_REGION.equals(Region.WEST_EUROPE))
			client = containerClient;
		else
			client = replicationContainerClient;

		if (path == null)
			return error(BAD_REQUEST);

		var blobId = path.replace("/", "+");
		var owner = path.split("/")[0];
		var blobData = RedisCache.getRecentBlob(blobId);
		if(blobData != null) {
			RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);
			return Result.ok(blobData.getBytes());
		}

		byte[] bytes = null;
		var blob = client.getBlobClient(path);

		try {
			BinaryData data = blob.downloadContent();
			bytes = data.toBytes();
			RedisCache.addRecentBlob(new Blob(blobId, owner, bytes));
		}
		catch(BlobStorageException e) {
			if(e.getStatusCode() == BLOB_NOT_FOUND)
				return error(NOT_FOUND);
		}

		if(bytes == null)
			return error( INTERNAL_ERROR );

		if(!TukanoApplication.REDIS_CACHE_ON)
			DB.updateViews(blobId, 1);
		else
			RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);

		return ok( bytes );
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);

		var blob = containerClient.getBlobClient(path);

		if(!blob.exists())
			return error(NOT_FOUND);

		long blobSize = blob.getProperties().getBlobSize();
		long offset = 0;

		while (offset < blobSize) {
			long nBytes = Math.min(CHUNK_SIZE, blobSize - offset);
			BlobRange range = new BlobRange(offset, nBytes);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			blob.downloadStreamWithResponse(outputStream, range, null, null, false, null, null);

			byte[] chunk = outputStream.toByteArray();
			sink.accept(chunk);

			offset += nBytes;
		}

		return ok();
	}
	
	@Override
	public Result<Void> delete(String path) {
		var res = execDelete(path, TukanoApplication.PRIMARY_REGION);
		if(res.isOK() && TukanoApplication.BLOBS_GEO_REPLICATION) {
			Executors.defaultThreadFactory().newThread(() ->
					execDelete(path, TukanoApplication.SECONDARY_REGION));
		}
		return res;
	}

	public Result<Void> execDelete(String path, Region region) {
		BlobContainerClient client;
		if(region.equals(Region.WEST_EUROPE) || !TukanoApplication.BLOBS_GEO_REPLICATION)
			client = containerClient;
		else
			client = replicationContainerClient;

		if (path == null)
			return error(BAD_REQUEST);

		var blob = client.getBlobClient(path);
		var blobId = path.replace("/", "+");
		var owner = path.split("/")[0];

		if(blob.deleteIfExists()) {
			RedisCache.removeBlobById(blobId);
			return ok();
		}

		else {
			var blobs = client.listBlobsByHierarchy(path + "/");

			if (!blobs.iterator().hasNext())
				return error(NOT_FOUND);

			else {
				blobs.forEach(blobItem -> {
					String blobName = blobItem.getName();
					client.getBlobClient(blobName).delete();
				});

				RedisCache.removeBlobsByOwner(owner);
				return ok();
			}
		}
	}



}
