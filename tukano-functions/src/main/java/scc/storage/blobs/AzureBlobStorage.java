package scc.storage.blobs;


import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import scc.data.Blob;
import scc.db.DB;
import scc.utils.RedisCache;
import scc.utils.Result;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import static scc.serverless.HttpFunction.BLOB_STORAGE_KEY;
import static scc.utils.RedisCache.REDIS_CACHE_ON;
import static scc.utils.RedisCache.VIEWS_KEY_PREFIX;
import static scc.utils.Result.ErrorCode.*;
import static scc.utils.Result.error;
import static scc.utils.Result.ok;

public class AzureBlobStorage implements BlobStorage {
	private static final int CHUNK_SIZE = 4096;
	private static final int BLOB_CONFLICT = 409;
	private static final int BLOB_NOT_FOUND = 404;
	private static final String VIDEOS_CONTAINER = "videos";
	private final BlobContainerClient containerClient;


	public AzureBlobStorage() {
		containerClient = new BlobServiceClientBuilder()
				.connectionString(BLOB_STORAGE_KEY)
				.buildClient()
				.createBlobContainerIfNotExists(VIDEOS_CONTAINER);

		containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
	}
	
	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);

		var blob = containerClient.getBlobClient(path);
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
		if (path == null)
			return error(BAD_REQUEST);

		var blobId = path.replace("/", "+");
		var owner = path.split("/")[0];
		var blobData = RedisCache.getRecentBlob(blobId);
		if(blobData != null) {
			RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);
			return ok(blobData.getBytes());
		}

		byte[] bytes = null;
		var blob = containerClient.getBlobClient(path);

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

		if(!REDIS_CACHE_ON)
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
		if (path == null)
			return error(BAD_REQUEST);

		var blob = containerClient.getBlobClient(path);
		var blobId = path.replace("/", "+");
		var owner = path.split("/")[0];

		if(blob.deleteIfExists()) {
			RedisCache.removeBlobById(blobId);
			return ok();
		}

		else {
			var blobs = containerClient.listBlobsByHierarchy(path + "/");

			if (!blobs.iterator().hasNext())
				return error(NOT_FOUND);

			else {
				blobs.forEach(blobItem -> {
					String blobName = blobItem.getName();
					containerClient.getBlobClient(blobName).delete();
				});

				RedisCache.removeBlobsByOwner(owner);
				return ok();
			}
		}
	}



}
