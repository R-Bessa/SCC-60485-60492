package tukano.impl.storage.db.azure;


import org.hibernate.Session;
import tukano.api.Result;
import tukano.impl.storage.db.Database;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class CosmosPostgreSQL implements Database {

    synchronized public static CosmosNoSQL getInstance() {
        // TODO
        return null;
    }
    @Override
    public <T> Result<T> persistOne(T obj) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<?> deleteOne(T obj) {
        // TODO
        return null;
    }

    @Override
    public <T> void deleteAll(Class<T> clazz, Session s, String... args) {
        // TODO

    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<List<T>> getAll(Class<T> clazz, String container, String... args) {
        return null;
    }

    @Override
    public <T> Result<List<T>> countAll(Class<T> clazz, String container, String attribute, String id) {
        return null;
    }

    @Override
    public <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match) {
        return null;
    }

    @Override
    public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<T> execute(Consumer<Session> proc) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<T> execute(Function<Session, Result<T>> func) {
        // TODO
        return null;
    }

    @Override
    public <T> Result<List<T>> searchPattern(Class<T> clazz, String pattern, String container, String attribute) {
        return null;
    }
}
