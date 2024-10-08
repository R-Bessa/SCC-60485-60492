package tukano.impl.storage.db;

import org.hibernate.Session;
import tukano.api.Result;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Database {
    <T> Result<T>  persistOne(T  obj);

    <T> Result<T> updateOne(T obj);

    <T> Result<T> deleteOne(T obj);

    <T> Result<T> getOne(String id, Class<T> clazz);

    <T> List<T> sql(String sqlStatement, Class<T> clazz);

    <T> Result<T> execute(Consumer<Session> proc);

    <T> Result<T> execute(Function<Session, Result<T>> func);
}
