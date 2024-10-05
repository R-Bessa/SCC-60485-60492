package tukano.impl.storage;


import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.function.Consumer;

import com.azure.core.exception.AzureException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.PublicAccessType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import tukano.api.Result;
import tukano.impl.rest.TukanoApplication;
import utils.Hash;
import utils.IO;

public class AzureBlobStorage implements BlobStorage {
	private static final int CHUNK_SIZE = 4096;
	private static final int BLOB_CONFLICT = 409;
	private static final int BLOB_NOT_FOUND = 404;
	private static final String VIDEOS_CONTAINER = "videos";
	private final BlobContainerClient containerClient;


	public AzureBlobStorage() {
		containerClient = new BlobServiceClientBuilder()
				.connectionString(TukanoApplication.BLOB_STORAGE_KEY)
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

		byte[] bytes = null;
		var blob = containerClient.getBlobClient(path);

		try {
			BinaryData data = blob.downloadContent();
			bytes = data.toBytes();
		}
		catch(BlobStorageException e) {
			if(e.getStatusCode() == BLOB_NOT_FOUND)
				return error(NOT_FOUND);
		}

		return bytes != null ? ok( bytes ) : error( INTERNAL_ERROR );
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

		if(blob.deleteIfExists())
			return ok();

		else {
			var blobs = containerClient.listBlobsByHierarchy(path + "/");

			if (!blobs.iterator().hasNext())
				return error(NOT_FOUND);

			else {
				blobs.forEach(blobItem -> {
					String blobName = blobItem.getName();
					containerClient.getBlobClient(blobName).delete();
				});

				return ok();
			}
		}
	}



}
