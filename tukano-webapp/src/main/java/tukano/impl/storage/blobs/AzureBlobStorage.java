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
import tukano.impl.georeplication.Region;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.cache.RedisCache;
import tukano.impl.storage.db.DB;

public class AzureBlobStorage implements BlobStorage {
	private static final int CHUNK_SIZE = 4096;
	private static final int BLOB_CONFLICT = 409;
	private static final int BLOB_NOT_FOUND = 404;
	private static final String VIDEOS_CONTAINER = "videos";

	private final BlobContainerClient primaryClient = init(TukanoApplication.BLOB_STORAGE_KEY);
	private final BlobContainerClient secondaryClient = init(TukanoApplication.SECONDARY_BLOB_STORAGE_KEY);


	private BlobContainerClient init(String key) {
		BlobContainerClient containerClient = new BlobServiceClientBuilder()
				.connectionString(key)
				.buildClient()
				.createBlobContainerIfNotExists(VIDEOS_CONTAINER);
		containerClient.setAccessPolicy(PublicAccessType.BLOB, null);

		return containerClient;
	}

	
	@Override
	public Result<Void> write(String path, byte[] bytes) {
		var res = execWrite(path, bytes, primaryClient);
		if(res.isOK() && TukanoApplication.BLOBS_GEO_REPLICATION) {
			Executors.defaultThreadFactory().newThread(() ->
				execWrite(path, bytes, secondaryClient)).start();
		}
		return res;
	}

	private Result<Void> execWrite(String path, byte[] bytes, BlobContainerClient client) {
		if (path == null)
			return error(BAD_REQUEST);

		var blob = client.getBlobClient(path);
		var data = BinaryData.fromBytes(bytes);

		try {
			blob.upload(data);

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
		if(TukanoApplication.CURRENT_REGION.equals(Region.WEST_EUROPE))
			client = primaryClient;
		else
			client = secondaryClient;

		if (path == null)
			return error(BAD_REQUEST);

		var blobId = path.replace("/", "+");

		byte[] bytes = null;
		var blob = client.getBlobClient(path);

		try {
			BinaryData data = blob.downloadContent();
			bytes = data.toBytes();

		} catch(BlobStorageException e) {
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

		var blob = primaryClient.getBlobClient(path);

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
		var res = execDelete(path, primaryClient);
		if(res.isOK() && TukanoApplication.BLOBS_GEO_REPLICATION) {
			Executors.defaultThreadFactory().newThread(() ->
					execDelete(path, secondaryClient)).start();
		}
		return res;
	}

	public Result<Void> execDelete(String path, BlobContainerClient client) {
		if (path == null)
			return error(BAD_REQUEST);

		var blob = client.getBlobClient(path);
		if(blob.deleteIfExists())
			return ok();

		else {
			var blobs = client.listBlobsByHierarchy(path + "/");

			if (!blobs.iterator().hasNext())
				return error(NOT_FOUND);

			else {
				blobs.forEach(blobItem -> {
					String blobName = blobItem.getName();
					client.getBlobClient(blobName).delete();
				});

				return ok();
			}
		}
	}



}
