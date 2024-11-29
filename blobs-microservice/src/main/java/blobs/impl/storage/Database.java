package blobs.impl.storage;

import blobs.api.Result;

public interface Database {

    <T> Result<T> getOne(String id, Class<T> clazz);

}
