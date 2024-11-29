package blobs.impl.storage.blobs;

import blobs.api.Result;

import java.util.function.Consumer;

public interface BlobStorage {
		
	public Result<Void> write(String path, byte[] bytes);
		
	public Result<Void> delete(String path);
	
	public Result<byte[]> read(String path);

	public Result<Void> read(String path, Consumer<byte[]> sink);

}
