package blobs.impl.storage.blobs;


import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import blobs.api.Result;
import blobs.impl.rest.BlobsMicroService;
import blobs.impl.storage.cache.RedisCache;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import static blobs.api.Result.ErrorCode.*;
import static blobs.api.Result.error;
import static blobs.api.Result.ok;
import static blobs.impl.storage.cache.RedisCache.VIEWS_KEY_PREFIX;

public class AzureBlobStorage implements BlobStorage {
	private static final int CHUNK_SIZE = 4096;
	private static final int BLOB_CONFLICT = 409;
	private static final int BLOB_NOT_FOUND = 404;
	private static final String VIDEOS_CONTAINER = "videos";

	private final BlobContainerClient client = init(BlobsMicroService.BLOB_STORAGE_KEY);

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
		return execWrite(path, bytes, client);
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

		RedisCache.incrCounter(VIEWS_KEY_PREFIX, blobId);

		return ok( bytes );
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);

		var blob = client.getBlobClient(path);

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
		return execDelete(path, client);
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
